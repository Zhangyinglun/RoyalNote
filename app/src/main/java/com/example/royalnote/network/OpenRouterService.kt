package com.example.royalnote.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenRouterService : RecordParser {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun parseRecords(text: String): ParsedRecords = withContext(Dispatchers.IO) {
        val systemPrompt = """你是一个起居注解析助手。用户会粘贴之前的生活记录文字，你需要将其解析为结构化的 JSON 数据。

规则：
1. 将文本拆分为多条独立的事件记录，每个事件一条记录
2. eventText: 简洁描述事件内容
3. moodTag: 根据事件内容推断心情，从以下选项中选择：开心、满足、平静、疲惫、烦躁、低落、焦虑。无法推断时设为 null
4. moodNote: 如果文本中有情绪描述，提取为 moodNote；没有则设为 null
5. timestamp: ISO 8601 格式（YYYY-MM-DDTHH:mm:ss）
   - 从文本中提取时间信息
   - 某个时间点下有多条记录时，合理推断它们共享该时间
   - 只有日期没有具体时间的，用当日 00:00
   - 完全没有时间信息的，用当前时间

只返回 JSON，不要返回其他内容：
{"records": [{"eventText": "...", "moodTag": "...", "moodNote": null, "timestamp": "YYYY-MM-DDTHH:mm:ss"}]}"""

        val requestBody = ChatCompletionRequest(
            model = OpenRouterConfig.MODEL,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = text),
            ),
            response_format = ResponseFormat(type = "json_object"),
            reasoning = ReasoningConfig(effort = "high", exclude = true),
            max_tokens = OpenRouterConfig.MAX_OUTPUT_TOKENS,
        )

        val bodyString = json.encodeToString(ChatCompletionRequest.serializer(), requestBody)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(OpenRouterConfig.BASE_URL)
            .addHeader("Authorization", "Bearer ${OpenRouterConfig.API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(bodyString)
            .build()

        val response = client.newCall(request).execute()

        response.use {
            if (!it.isSuccessful) {
                throw IOException("API error: ${it.code}")
            }
            val responseBody = it.body?.string()
            if (responseBody == null) {
                throw IOException("Empty response body")
            }
            val apiResponse = json.decodeFromString(ChatCompletionResponse.serializer(), responseBody)
            val content = apiResponse.choices.firstOrNull()?.message?.content
                ?: throw IOException("No content in response")
            json.decodeFromString(ParsedRecords.serializer(), content)
        }
    }
}
