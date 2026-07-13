package com.example.royalnote.network

import com.example.royalnote.settings.OpenRouterRequestSettings
import com.example.royalnote.settings.OpenRouterSettingsProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterSettingsServiceTest {
    @Test
    fun blankConfiguredKeyFailsBeforeNetwork() = runTest {
        val service = OpenRouterService(
            settingsProvider = OpenRouterSettingsProvider {
                OpenRouterRequestSettings("", "deepseek/deepseek-v4-pro", "high")
            },
        )

        val failure = runCatching {
            service.parseRecords("昨天读书")
        }.exceptionOrNull()

        assertTrue(failure is MissingOpenRouterApiKeyException)
    }
}
