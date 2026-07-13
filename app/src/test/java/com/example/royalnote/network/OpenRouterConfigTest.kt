package com.example.royalnote.network

import com.example.royalnote.settings.OpenRouterRequestSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterConfigTest {
    @Test
    fun importRequestUsesSelectedModelEffortAndLongOutputBudget() {
        val request = buildChatCompletionRequest(
            text = "long import text",
            systemPrompt = "system",
            settings = OpenRouterRequestSettings(
                apiKey = "secret",
                modelId = "~openai/gpt-latest",
                effort = "xhigh",
            ),
        )

        val encoded = Json.encodeToString(ChatCompletionRequest.serializer(), request)
        assertEquals("~openai/gpt-latest", request.model)
        assertEquals("xhigh", request.reasoning?.effort)
        assertEquals(OpenRouterConfig.MAX_OUTPUT_TOKENS, request.max_tokens)
        assertTrue(encoded.contains("\"max_tokens\":${OpenRouterConfig.MAX_OUTPUT_TOKENS}"))
    }
}
