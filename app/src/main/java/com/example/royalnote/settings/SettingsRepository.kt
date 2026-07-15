package com.example.royalnote.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val apiKey: String = "",
    val themeMode: AppThemeMode = AppThemeMode.AUTO,
    val selectedModel: AnalysisModel = AnalysisModel.DEEPSEEK_V4_PRO,
    val efforts: Map<AnalysisModel, ReasoningEffort> = AnalysisModel.entries.associateWith {
        ReasoningEffort.HIGH
    },
) {
    fun effortFor(model: AnalysisModel): ReasoningEffort = efforts[model]
        ?.takeIf { it in model.supportedEfforts }
        ?: ReasoningEffort.HIGH

    val selectedEffort: ReasoningEffort get() = effortFor(selectedModel)
}

data class OpenRouterRequestSettings(
    val apiKey: String,
    val modelId: String,
    val effort: String,
)

fun interface OpenRouterSettingsProvider {
    fun currentSettings(): OpenRouterRequestSettings
}

interface SettingsStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

class SharedPreferencesSettingsStorage(context: Context) : SettingsStorage {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    companion object {
        const val FILE_NAME = "openrouter_settings"
    }
}

class SettingsRepository(
    private val storage: SettingsStorage,
) : OpenRouterSettingsProvider {
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun updateApiKey(value: String) {
        storage.putString(SettingsKeys.API_KEY, value)
        _settings.value = _settings.value.copy(apiKey = value)
    }

    fun selectThemeMode(mode: AppThemeMode) {
        storage.putString(SettingsKeys.THEME_MODE, mode.storageValue)
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun selectModel(model: AnalysisModel) {
        storage.putString(SettingsKeys.SELECTED_MODEL, model.storageValue)
        _settings.value = _settings.value.copy(selectedModel = model)
    }

    fun selectEffort(model: AnalysisModel, effort: ReasoningEffort) {
        if (effort !in model.supportedEfforts) return
        storage.putString(SettingsKeys.effort(model), effort.wireValue)
        _settings.value = _settings.value.copy(
            efforts = _settings.value.efforts + (model to effort),
        )
    }

    override fun currentSettings(): OpenRouterRequestSettings {
        val current = _settings.value
        return OpenRouterRequestSettings(
            apiKey = current.apiKey.trim(),
            modelId = current.selectedModel.openRouterId,
            effort = current.selectedEffort.wireValue,
        )
    }

    private fun load(): AppSettings {
        val selectedModel = AnalysisModel.fromStorageValue(
            storage.getString(SettingsKeys.SELECTED_MODEL)
        ) ?: AnalysisModel.DEEPSEEK_V4_PRO
        val efforts = AnalysisModel.entries.associateWith { model ->
            ReasoningEffort.fromWireValue(storage.getString(SettingsKeys.effort(model)))
                ?.takeIf { it in model.supportedEfforts }
                ?: ReasoningEffort.HIGH
        }
        return AppSettings(
            apiKey = storage.getString(SettingsKeys.API_KEY).orEmpty(),
            themeMode = AppThemeMode.fromStorageValue(
                storage.getString(SettingsKeys.THEME_MODE)
            ) ?: AppThemeMode.AUTO,
            selectedModel = selectedModel,
            efforts = efforts,
        )
    }
}

internal object SettingsKeys {
    const val API_KEY = "api_key"
    const val THEME_MODE = "theme_mode"
    const val SELECTED_MODEL = "selected_model"
    fun effort(model: AnalysisModel): String = "effort_${model.storageValue}"
}
