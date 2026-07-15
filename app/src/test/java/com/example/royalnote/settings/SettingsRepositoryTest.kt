package com.example.royalnote.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryTest {
    @Test
    fun defaultsUseAutomaticThemeDeepSeekAndHighForEveryModel() {
        val repository = SettingsRepository(FakeSettingsStorage())

        assertEquals(AppThemeMode.AUTO, repository.settings.value.themeMode)
        assertEquals(AnalysisModel.DEEPSEEK_V4_PRO, repository.settings.value.selectedModel)
        AnalysisModel.entries.forEach { model ->
            assertEquals(ReasoningEffort.HIGH, repository.settings.value.effortFor(model))
        }
    }

    @Test
    fun modelCatalogExposesOnlySupportedEfforts() {
        assertEquals(
            listOf(ReasoningEffort.XHIGH, ReasoningEffort.HIGH),
            AnalysisModel.DEEPSEEK_V4_PRO.supportedEfforts,
        )
        assertEquals(
            listOf(
                ReasoningEffort.MAX,
                ReasoningEffort.XHIGH,
                ReasoningEffort.HIGH,
                ReasoningEffort.MEDIUM,
                ReasoningEffort.LOW,
                ReasoningEffort.NONE,
            ),
            AnalysisModel.GPT_LATEST.supportedEfforts,
        )
        assertEquals(
            listOf(ReasoningEffort.HIGH, ReasoningEffort.MEDIUM, ReasoningEffort.LOW),
            AnalysisModel.GEMINI_PRO_LATEST.supportedEfforts,
        )
    }

    @Test
    fun changesPersistAndReloadWithPerModelEffortMemory() {
        val storage = FakeSettingsStorage()
        val repository = SettingsRepository(storage)

        repository.updateApiKey(" sk-or-v1-test ")
        repository.selectThemeMode(AppThemeMode.DARK)
        repository.selectModel(AnalysisModel.GPT_LATEST)
        repository.selectEffort(AnalysisModel.GPT_LATEST, ReasoningEffort.MAX)
        repository.selectEffort(AnalysisModel.DEEPSEEK_V4_PRO, ReasoningEffort.XHIGH)

        val reloaded = SettingsRepository(storage)
        assertEquals(" sk-or-v1-test ", reloaded.settings.value.apiKey)
        assertEquals(AppThemeMode.DARK, reloaded.settings.value.themeMode)
        assertEquals(AnalysisModel.GPT_LATEST, reloaded.settings.value.selectedModel)
        assertEquals(ReasoningEffort.MAX, reloaded.settings.value.effortFor(AnalysisModel.GPT_LATEST))
        assertEquals(ReasoningEffort.XHIGH, reloaded.settings.value.effortFor(AnalysisModel.DEEPSEEK_V4_PRO))
    }

    @Test
    fun unsupportedEffortIsIgnoredAndInvalidStoredValuesFallBack() {
        val storage = FakeSettingsStorage(
            mutableMapOf(
                "selected_model" to "removed-model",
                "theme_mode" to "removed-theme",
                "effort_deepseek-v4-pro" to "low",
                "effort_gpt-latest" to "removed-effort",
            )
        )
        val repository = SettingsRepository(storage)

        assertEquals(AppThemeMode.AUTO, repository.settings.value.themeMode)
        assertEquals(AnalysisModel.DEEPSEEK_V4_PRO, repository.settings.value.selectedModel)
        assertEquals(ReasoningEffort.HIGH, repository.settings.value.effortFor(AnalysisModel.DEEPSEEK_V4_PRO))
        assertEquals(ReasoningEffort.HIGH, repository.settings.value.effortFor(AnalysisModel.GPT_LATEST))

        repository.selectEffort(AnalysisModel.DEEPSEEK_V4_PRO, ReasoningEffort.LOW)
        assertEquals(ReasoningEffort.HIGH, repository.settings.value.effortFor(AnalysisModel.DEEPSEEK_V4_PRO))
    }

    @Test
    fun currentSettingsReturnsTrimmedImmutableRequestSnapshot() {
        val repository = SettingsRepository(FakeSettingsStorage())
        repository.updateApiKey("  sk-or-v1-test  ")
        repository.selectModel(AnalysisModel.GEMINI_PRO_LATEST)
        repository.selectEffort(AnalysisModel.GEMINI_PRO_LATEST, ReasoningEffort.LOW)

        assertEquals(
            OpenRouterRequestSettings(
                apiKey = "sk-or-v1-test",
                modelId = "~google/gemini-pro-latest",
                effort = "low",
            ),
            repository.currentSettings(),
        )
    }
}

private class FakeSettingsStorage(
    private val values: MutableMap<String, String> = mutableMapOf(),
) : SettingsStorage {
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) {
        values[key] = value
    }
}
