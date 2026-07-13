package com.example.royalnote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.royalnote.data.MoodLabels
import com.example.royalnote.data.NoteRecord
import com.example.royalnote.network.ParsedRecord
import com.example.royalnote.network.RecordParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

data class ImportUiState(
    val text: String = "",
    val isLoading: Boolean = false,
    val message: String? = null,
    val successDialogMessage: String? = null,
    val isSuccess: Boolean = false,
)

class ImportViewModel(
    private val parser: RecordParser,
    private val repository: RecordOperations,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()
    private var activeImportJob: Job? = null
    private var importGeneration = 0L

    fun updateText(value: String) {
        _uiState.value = _uiState.value.copy(text = value, message = null)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun dismissSuccessDialog() {
        _uiState.value = _uiState.value.copy(successDialogMessage = null)
    }

    fun resetState() {
        importGeneration++
        activeImportJob?.cancel()
        activeImportJob = null
        _uiState.value = ImportUiState()
    }

    fun importRecords() {
        val state = _uiState.value
        if (activeImportJob?.isActive == true || state.isLoading) return

        val currentText = state.text
        if (currentText.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "先粘贴旧日记录")
            return
        }

        val operationGeneration = ++importGeneration
        _uiState.value = _uiState.value.copy(isLoading = true, message = null)

        activeImportJob = viewModelScope.launch {
            try {
                val records = try {
                    val parsed = parser.parseRecords(currentText)
                    val importedAt = clock.millis()
                    parsed.records.map { it.toNoteRecord(importedAt) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    updateState(operationGeneration) { it.copy(message = importFailureMessage(e)) }
                    return@launch
                }

                try {
                    repository.importRecords(records)
                    updateState(operationGeneration) {
                        it.copy(
                            isSuccess = true,
                            message = null,
                            successDialogMessage = "已入录 ${records.size} 则",
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    updateState(operationGeneration) {
                        it.copy(message = "记录已解析，但保存失败，请稍后再试")
                    }
                }
            } finally {
                updateState(operationGeneration) { it.copy(isLoading = false) }
            }
        }
    }

    private inline fun updateState(
        operationGeneration: Long,
        transform: (ImportUiState) -> ImportUiState,
    ) {
        if (operationGeneration == importGeneration) {
            _uiState.value = transform(_uiState.value)
        }
    }

    private fun ParsedRecord.toNoteRecord(importedAt: Long): NoteRecord {
        val range = parseRange(startedAt, endedAt, importedAt)
        return NoteRecord(
            eventText = eventText,
            moodTag = moodTag?.takeIf { it in MoodLabels.ALL },
            moodNote = moodNote?.takeIf { it.isNotBlank() },
            startedAt = range.first,
            endedAt = range.second,
            createdAt = importedAt,
            updatedAt = importedAt,
        )
    }

    private fun parseRange(start: String, end: String, fallback: Long): Pair<Long, Long> {
        val startedAt = parseTimestampOrNull(start)
        val endedAt = parseTimestampOrNull(end)
        return if (startedAt == null || endedAt == null || endedAt < startedAt) {
            fallback to fallback
        } else {
            startedAt to endedAt
        }
    }

    private fun parseTimestampOrNull(timestamp: String): Long? {
        return runCatching {
            LocalDateTime.parse(timestamp)
                .atZone(clock.zone)
                .toInstant()
                .toEpochMilli()
        }.recoverCatching {
            LocalDate.parse(timestamp)
                .atStartOfDay(clock.zone)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }
}

class ImportViewModelFactory(
    private val parser: RecordParser,
    private val repository: RecordOperations,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ImportViewModel(parser, repository) as T
    }
}
