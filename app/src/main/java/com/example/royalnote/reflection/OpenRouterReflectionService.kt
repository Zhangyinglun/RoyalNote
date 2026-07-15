package com.example.royalnote.reflection

import com.example.royalnote.network.ChatCompletionRequest
import com.example.royalnote.network.ChatCompletionResponse
import com.example.royalnote.network.ChatMessage
import com.example.royalnote.network.MissingOpenRouterApiKeyException
import com.example.royalnote.network.OpenRouterConfig
import com.example.royalnote.network.OpenRouterResponseException
import com.example.royalnote.network.ReasoningConfig
import com.example.royalnote.network.ResponseFormat
import com.example.royalnote.network.awaitResponse
import com.example.royalnote.settings.OpenRouterRequestSettings
import com.example.royalnote.settings.OpenRouterSettingsProvider
import java.io.IOException
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenRouterReflectionService(
    private val settingsProvider: OpenRouterSettingsProvider,
    private val prompts: ReflectionPromptProvider,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val client: Call.Factory = defaultReflectionClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val chatUrl: String = OpenRouterConfig.CHAT_COMPLETIONS_URL,
    private val blockingDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReflectionAiGateway {
    private val requestJson = Json(json) { encodeDefaults = true }

    override suspend fun generateReflection(input: ReflectionGenerationInput): SevenDayReflection {
        val recordsJson = boundedRecordsJson(input.records)
        val userData = """
            本次回顾日期：${input.period.startDate} 至 ${input.period.endDate}
            本地时区：${clock.zone.id}
            已确认长期记忆（可能为空）：
            <memory>
            ${input.memoryMarkdown.bounded(4_000)}
            </memory>
            七日起居记录 JSON：
            <records>
            $recordsJson
            </records>
        """.trimIndent()
        return completeJson(
            systemPrompt = prompts.sevenDayReflection,
            userContent = userData,
            serializer = SevenDayReflection.serializer(),
            maxOutputTokens = 8_192,
        )
    }

    override suspend fun chat(input: ReflectionChatInput): ReflectionChatResult {
        val reflectionJson = requestJson.encodeToString(
            SevenDayReflection.serializer(),
            input.reflection,
        )
        val recordsJson = boundedRecordsJson(input.records)
        val messagesJson = requestJson.encodeToString(
            ListSerializer(NetworkConversationMessage.serializer()),
            input.recentMessages.map {
                NetworkConversationMessage(it.role.wireValue, it.content.bounded(4_000))
            },
        )
        val userData = """
            线程日期：${input.threadDate}
            固定七日回顾：
            <reflection>$reflectionJson</reflection>
            对应的起居记录快照：
            <records>$recordsJson</records>
            当前已确认长期记忆：
            <memory>${input.memoryMarkdown.bounded(4_000)}</memory>
            较早对话的滚动摘要：
            <conversation_summary>${input.conversationSummary.bounded(4_000)}</conversation_summary>
            本线程近期消息（最后一条 user 消息是本次需要回复的内容）：
            <messages>$messagesJson</messages>
        """.trimIndent()
        return completeJson(
            systemPrompt = prompts.reflectionChat,
            userContent = userData,
            serializer = ReflectionChatResult.serializer(),
            maxOutputTokens = 2_048,
        )
    }

    override suspend fun compactConversation(
        existingSummary: String,
        messages: List<ReflectionConversationMessage>,
    ): String {
        val userData = """
            已有摘要：
            <existing>${existingSummary.bounded(4_000)}</existing>
            需要并入的较早对话：
            <messages>
            ${messages.joinToString("\n") { "${it.role.wireValue}: ${it.content.bounded(2_000)}" }}
            </messages>
        """.trimIndent()
        return completeJson(
            systemPrompt = prompts.conversationCompaction,
            userContent = userData,
            serializer = CompactionResponse.serializer(),
            maxOutputTokens = 2_048,
        ).summary.bounded(4_000)
    }

    private suspend fun <T> completeJson(
        systemPrompt: String,
        userContent: String,
        serializer: DeserializationStrategy<T>,
        maxOutputTokens: Int,
    ): T = withContext(blockingDispatcher) {
        val settings = settingsProvider.currentSettings()
        if (settings.apiKey.isBlank()) throw MissingOpenRouterApiKeyException()
        val requestBody = reflectionRequest(
            settings = settings,
            systemPrompt = systemPrompt,
            userContent = userContent,
            maxOutputTokens = maxOutputTokens,
        )
        val body = requestJson.encodeToString(ChatCompletionRequest.serializer(), requestBody)
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(chatUrl)
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        val call = client.newCall(request)
        try {
            call.awaitResponse().use { response ->
                if (!response.isSuccessful) {
                    throw OpenRouterResponseException("OpenRouter response code ${response.code}")
                }
                try {
                    runInterruptible(blockingDispatcher) {
                        val apiResponse = json.decodeFromString(
                            ChatCompletionResponse.serializer(),
                            response.body.string(),
                        )
                        val content = apiResponse.choices.firstOrNull()?.message?.content
                            ?.takeIf(String::isNotBlank)
                            ?: throw OpenRouterResponseException("OpenRouter response content is missing")
                        json.decodeFromString(serializer, content)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: IOException) {
                    throw error
                } catch (error: OpenRouterResponseException) {
                    throw error
                } catch (error: Exception) {
                    throw OpenRouterResponseException("OpenRouter response is malformed", error)
                }
            }
        } catch (error: CancellationException) {
            call.cancel()
            throw error
        }
    }

    private fun boundedRecordsJson(records: List<ReflectionRecordSnapshot>): String {
        val included = mutableListOf<ReflectionRecordSnapshot>()
        records.forEach { record ->
            val bounded = record.copy(
                eventText = record.eventText.bounded(2_000),
                moodNote = record.moodNote?.bounded(1_000),
            )
            val candidate = NetworkRecordEnvelope(
                records = included + bounded,
                omittedRecordCount = records.size - included.size - 1,
            )
            val encoded = requestJson.encodeToString(NetworkRecordEnvelope.serializer(), candidate)
            if (encoded.length > ConversationContextBudget.SNAPSHOT_CHARACTERS && included.isNotEmpty()) {
                return requestJson.encodeToString(
                    NetworkRecordEnvelope.serializer(),
                    NetworkRecordEnvelope(included, records.size - included.size),
                )
            }
            included += bounded
        }
        return requestJson.encodeToString(
            NetworkRecordEnvelope.serializer(),
            NetworkRecordEnvelope(included, records.size - included.size),
        )
    }
}

internal fun reflectionRequest(
    settings: OpenRouterRequestSettings,
    systemPrompt: String,
    userContent: String,
    maxOutputTokens: Int,
) = ChatCompletionRequest(
    model = settings.modelId,
    messages = listOf(
        ChatMessage(role = "system", content = systemPrompt),
        ChatMessage(role = "user", content = userContent),
    ),
    response_format = ResponseFormat(type = "json_object"),
    reasoning = ReasoningConfig(effort = settings.effort, exclude = true),
    max_tokens = maxOutputTokens,
)

private fun defaultReflectionClient(): Call.Factory = object : Call.Factory {
    private val delegate by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()
    }

    override fun newCall(request: Request): Call = delegate.newCall(request)
}

@Serializable
private data class NetworkConversationMessage(val role: String, val content: String)

@Serializable
private data class NetworkRecordEnvelope(
    val records: List<ReflectionRecordSnapshot>,
    val omittedRecordCount: Int,
)

@Serializable
private data class CompactionResponse(val summary: String = "")

private fun String.bounded(maxCharacters: Int): String = if (length <= maxCharacters) this else
    take(maxCharacters) + "\n[内容因上下文预算被截断]"
