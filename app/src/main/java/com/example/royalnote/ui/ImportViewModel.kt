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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

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
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

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
        _uiState.value = ImportUiState()
    }

    fun importRecords() {
        val currentText = _uiState.value.text
        if (currentText.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "先粘贴旧日记录")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, message = null)

        viewModelScope.launch {
            try {
                val parsed = parser.parseRecords(currentText)
                val records = parsed.records.map { it.toNoteRecord() }
                repository.importRecords(records)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSuccess = true,
                    message = null,
                    successDialogMessage = "已入录 ${records.size} 则",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "网络不通，稍后再试",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "解析未成，稍后再试",
                )
            }
        }
    }

    private fun ParsedRecord.toNoteRecord(): NoteRecord {
        val millis = parseTimestamp(timestamp)
        val validMoods = MoodLabels.ALL.toSet()
        return NoteRecord(
            eventText = eventText,
            moodTag = moodTag?.takeIf { it in validMoods },
            moodNote = moodNote?.takeIf { it.isNotBlank() },
            createdAt = millis,
            updatedAt = millis,
        )
    }

    private fun parseTimestamp(timestamp: String): Long {
        return try {
            LocalDateTime.parse(timestamp)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (e: DateTimeParseException) {
            try {
                LocalDate.parse(timestamp)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (e: DateTimeParseException) {
                Instant.now().toEpochMilli()
            }
        }
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
