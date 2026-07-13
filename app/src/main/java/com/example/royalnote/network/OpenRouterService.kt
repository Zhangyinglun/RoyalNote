package com.example.royalnote.network

import com.example.royalnote.settings.OpenRouterRequestSettings
import com.example.royalnote.settings.OpenRouterSettingsProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.io.IOException
import java.util.concurrent.TimeUnit

class MissingOpenRouterApiKeyException : IllegalStateException("OpenRouter API key is missing")

class OpenRouterService(
    private val settingsProvider: OpenRouterSettingsProvider,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val client: Call.Factory = defaultOpenRouterClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val chatUrl: String = OpenRouterConfig.CHAT_COMPLETIONS_URL,
    private val blockingDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RecordParser {
    private val requestJson = Json(json) { encodeDefaults = true }

    override suspend fun parseRecords(text: String): ParsedRecords = withContext(blockingDispatcher) {
        val settings = settingsProvider.currentSettings()
        if (settings.apiKey.isBlank()) throw MissingOpenRouterApiKeyException()
        val requestBody = buildChatCompletionRequest(
            text = text,
            systemPrompt = buildSystemPrompt(clock),
            settings = settings,
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
                        val responseBody = response.body.string()
                        val apiResponse = json.decodeFromString(ChatCompletionResponse.serializer(), responseBody)
                        val content = apiResponse.choices.firstOrNull()?.message?.content
                            ?.takeIf(String::isNotBlank)
                            ?: throw OpenRouterResponseException("OpenRouter response content is missing")
                        json.decodeFromString(ParsedRecords.serializer(), content)
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
}

internal fun buildChatCompletionRequest(
    text: String,
    systemPrompt: String,
    settings: OpenRouterRequestSettings,
): ChatCompletionRequest = ChatCompletionRequest(
    model = settings.modelId,
    messages = listOf(
        ChatMessage(role = "system", content = systemPrompt),
        ChatMessage(role = "user", content = text),
    ),
    response_format = ResponseFormat(type = "json_object"),
    reasoning = ReasoningConfig(effort = settings.effort, exclude = true),
    max_tokens = OpenRouterConfig.MAX_OUTPUT_TOKENS,
)

private fun defaultOpenRouterClient(): Call.Factory = object : Call.Factory {
    private val delegate by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()
    }

    override fun newCall(request: Request): Call = delegate.newCall(request)
}

private val promptDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

internal fun buildSystemPrompt(clock: Clock): String {
    val currentDateTime = LocalDateTime.now(clock).format(promptDateTimeFormatter)
    return """你是一个起居注解析助手。用户会粘贴之前的生活记录文字，你需要将其解析为结构化的 JSON 数据。

当前本地日期时间：$currentDateTime
当前时区：${clock.zone.id}
“今天”“昨天”等相对日期，以及没有时间信息的情况，都以上述本地日期时间和时区为准。

规则：
1. 将文本拆分为多条独立的事件记录，每个事件一条记录
2. eventText: 简洁描述事件内容
3. moodTag: 根据事件内容推断心情，从以下选项中选择：开心、满足、平静、疲惫、烦躁、低落、焦虑。无法推断时设为 null
4. moodNote: 如果文本中有情绪描述，提取为 moodNote；没有则设为 null
5. startedAt 和 endedAt: ISO 本地日期时间格式（YYYY-MM-DDTHH:mm:ss）
   - 时间范围要分别提取开始和结束时间
   - 只有一个时间点时，两个字段填写相同时间
   - 某个时间点下有多条记录时，合理推断它们共享该时间
   - 只有日期没有具体时间时，两个字段都使用当日 00:00
   - 完全没有时间信息时，两个字段都使用当前时间

只返回 JSON，不要返回其他内容：
{"records": [{"eventText": "...", "moodTag": "...", "moodNote": null, "startedAt": "YYYY-MM-DDTHH:mm:ss", "endedAt": "YYYY-MM-DDTHH:mm:ss"}]}"""
}
