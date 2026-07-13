package com.example.royalnote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.royalnote.network.InvalidOpenRouterApiKeyException
import com.example.royalnote.network.OpenRouterUsageProvider
import com.example.royalnote.settings.AnalysisModel
import com.example.royalnote.settings.AppSettings
import com.example.royalnote.settings.ReasoningEffort
import com.example.royalnote.settings.SettingsRepository
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UsageUiState {
    data object Ready : UsageUiState
    data object MissingKey : UsageUiState
    data class Loading(val previousAmount: Double? = null) : UsageUiState
    data class Success(val amount: Double, val updatedAtMillis: Long) : UsageUiState
    data class Error(
        val message: String,
        val previousAmount: Double? = null,
        val previousUpdatedAtMillis: Long? = null,
    ) : UsageUiState
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val usage: UsageUiState = UsageUiState.MissingKey,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val usageProvider: OpenRouterUsageProvider,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val usageState = MutableStateFlow<UsageUiState>(
        if (repository.settings.value.apiKey.isBlank()) UsageUiState.MissingKey else UsageUiState.Ready
    )
    val uiState = combine(repository.settings, usageState) { settings, usage ->
        SettingsUiState(settings, usage)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(repository.settings.value, usageState.value),
    )
    private var usageJob: Job? = null

    fun updateApiKey(value: String) {
        usageJob?.cancel()
        repository.updateApiKey(value)
        usageState.value = if (value.isBlank()) UsageUiState.MissingKey else UsageUiState.Ready
    }

    fun selectModel(model: AnalysisModel) = repository.selectModel(model)

    fun selectEffort(model: AnalysisModel, effort: ReasoningEffort) =
        repository.selectEffort(model, effort)

    fun onScreenVisible() = refreshUsage()

    fun refreshUsage() {
        if (usageJob?.isActive == true) return
        val key = repository.settings.value.apiKey.trim()
        if (key.isBlank()) {
            usageState.value = UsageUiState.MissingKey
            return
        }
        val previous = previousSuccess()
        usageState.value = UsageUiState.Loading(previous?.first)
        usageJob = viewModelScope.launch {
            try {
                val usage = usageProvider.monthlyUsage(key)
                usageState.value = UsageUiState.Success(usage.amountUsd, clock.millis())
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidOpenRouterApiKeyException) {
                usageState.value = UsageUiState.Error(
                    "API Key 无效，请检查设置",
                    previous?.first,
                    previous?.second,
                )
            } catch (e: Exception) {
                usageState.value = UsageUiState.Error(
                    "用量查询失败，请稍后再试",
                    previous?.first,
                    previous?.second,
                )
            }
        }
    }

    private fun previousSuccess(): Pair<Double, Long>? = when (val state = usageState.value) {
        is UsageUiState.Success -> state.amount to state.updatedAtMillis
        is UsageUiState.Error -> state.previousAmount?.let {
            it to (state.previousUpdatedAtMillis ?: 0L)
        }
        else -> null
    }
}

class SettingsViewModelFactory(
    private val repository: SettingsRepository,
    private val usageProvider: OpenRouterUsageProvider,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(repository, usageProvider) as T
}
