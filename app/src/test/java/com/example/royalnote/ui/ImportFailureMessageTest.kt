package com.example.royalnote.ui

import com.example.royalnote.network.MissingOpenRouterApiKeyException
import com.example.royalnote.network.OpenRouterResponseException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportFailureMessageTest {
    @Test
    fun transportFailureUsesNetworkMessage() {
        assertEquals("网络不通，稍后再试", importFailureMessage(IOException("offline")))
    }

    @Test
    fun httpAndProtocolFailuresUseParseMessage() {
        listOf(
            OpenRouterResponseException("HTTP 400"),
            OpenRouterResponseException("HTTP 500"),
            OpenRouterResponseException("empty content"),
            OpenRouterResponseException("malformed response"),
        ).forEach { failure ->
            assertEquals("解析未成，稍后再试", importFailureMessage(failure))
        }
    }

    @Test
    fun missingKeyAndCancellationRemainDistinct() {
        assertEquals(
            "请先在设置中填写 OpenRouter API Key",
            importFailureMessage(MissingOpenRouterApiKeyException()),
        )
        assertNull(importFailureMessage(CancellationException("cancelled")))
    }
}
