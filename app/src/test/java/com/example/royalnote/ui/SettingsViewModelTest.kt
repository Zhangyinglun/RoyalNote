package com.example.royalnote.ui

import com.example.royalnote.network.InvalidOpenRouterApiKeyException
import com.example.royalnote.network.MonthlyUsage
import com.example.royalnote.network.OpenRouterUsageProvider
import com.example.royalnote.settings.AnalysisModel
import com.example.royalnote.settings.ReasoningEffort
import com.example.royalnote.settings.SettingsRepository
import com.example.royalnote.settings.SettingsStorage
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-07-12T12:34:56Z"), ZoneOffset.UTC)
    private lateinit var repository: SettingsRepository
    private lateinit var usage: FakeUsageProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = SettingsRepository(FakeSettingsStorage())
        usage = FakeUsageProvider()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun screenVisibleWithKeyLoadsMonthlyUsage() = runTest {
        repository.updateApiKey("sk-or-v1-test")
        usage.result = MonthlyUsage(12.34)
        val viewModel = SettingsViewModel(repository, usage, clock)

        viewModel.onScreenVisible()
        advanceUntilIdle()

        assertEquals(1, usage.calls)
        assertEquals(UsageUiState.Success(12.34, clock.millis()), viewModel.uiState.value.usage)
    }

    @Test
    fun blankKeyDoesNotCallService() = runTest {
        val viewModel = SettingsViewModel(repository, usage, clock)

        viewModel.onScreenVisible()
        advanceUntilIdle()

        assertEquals(0, usage.calls)
        assertEquals(UsageUiState.MissingKey, viewModel.uiState.value.usage)
    }

    @Test
    fun editingKeyClearsOldUsageWithoutQueryingEveryCharacter() = runTest {
        repository.updateApiKey("old-key")
        usage.result = MonthlyUsage(8.0)
        val viewModel = SettingsViewModel(repository, usage, clock)
        viewModel.refreshUsage()
        advanceUntilIdle()

        viewModel.updateApiKey("new-key")
        advanceUntilIdle()

        assertEquals(1, usage.calls)
        assertEquals(UsageUiState.Ready, viewModel.uiState.value.usage)
    }

    @Test
    fun failedRefreshKeepsLastSuccessfulAmount() = runTest {
        repository.updateApiKey("sk-or-v1-test")
        usage.result = MonthlyUsage(8.0)
        val viewModel = SettingsViewModel(repository, usage, clock)
        viewModel.refreshUsage()
        advanceUntilIdle()

        usage.error = IOException("offline")
        viewModel.refreshUsage()
        advanceUntilIdle()

        assertEquals(
            UsageUiState.Error("用量查询失败，请稍后再试", 8.0, clock.millis()),
            viewModel.uiState.value.usage,
        )
    }

    @Test
    fun unauthorizedRefreshShowsInvalidKeyMessage() = runTest {
        repository.updateApiKey("bad-key")
        usage.error = InvalidOpenRouterApiKeyException()
        val viewModel = SettingsViewModel(repository, usage, clock)

        viewModel.refreshUsage()
        advanceUntilIdle()

        assertEquals(
            UsageUiState.Error("API Key 无效，请检查设置"),
            viewModel.uiState.value.usage,
        )
    }

    @Test
    fun modelAndEffortCallbacksDelegateToRepository() = runTest {
        val viewModel = SettingsViewModel(repository, usage, clock)

        viewModel.selectModel(AnalysisModel.GPT_LATEST)
        viewModel.selectEffort(AnalysisModel.GPT_LATEST, ReasoningEffort.MAX)
        advanceUntilIdle()

        assertEquals(AnalysisModel.GPT_LATEST, repository.settings.value.selectedModel)
        assertEquals(ReasoningEffort.MAX, repository.settings.value.effortFor(AnalysisModel.GPT_LATEST))
        assertEquals(repository.settings.value, viewModel.uiState.value.settings)
    }
}

private class FakeUsageProvider : OpenRouterUsageProvider {
    var result = MonthlyUsage(0.0)
    var error: Throwable? = null
    var calls = 0
        private set

    override suspend fun monthlyUsage(apiKey: String): MonthlyUsage {
        calls++
        error?.let { throw it }
        return result
    }
}

private class FakeSettingsStorage : SettingsStorage {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }
}
