package com.example.royalnote.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterConfigTest {
    @Test
    fun importRequestUsesDeepSeekFlashHighWithLongOutputBudget() {
        val request = ChatCompletionRequest(
            model = OpenRouterConfig.MODEL,
            messages = listOf(ChatMessage(role = "user", content = "long import text")),
            response_format = ResponseFormat(type = "json_object"),
            reasoning = ReasoningConfig(effort = "high", exclude = true),
            max_tokens = OpenRouterConfig.MAX_OUTPUT_TOKENS,
        )

        val encoded = Json.encodeToString(ChatCompletionRequest.serializer(), request)

        assertEquals("deepseek/deepseek-v4-flash", request.model)
        assertEquals("high", request.reasoning?.effort)
        assertEquals(OpenRouterConfig.MAX_OUTPUT_TOKENS, request.max_tokens)
        assertTrue(OpenRouterConfig.MAX_OUTPUT_TOKENS >= 32_768)
        assertTrue(encoded.contains("\"max_tokens\":${OpenRouterConfig.MAX_OUTPUT_TOKENS}"))
    }
}
