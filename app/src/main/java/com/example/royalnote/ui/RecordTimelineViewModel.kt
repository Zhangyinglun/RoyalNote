package com.example.royalnote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.royalnote.data.NoteRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

interface RecordOperations {
    fun observeRecords(): Flow<List<NoteRecord>>

    suspend fun addRecord(
        eventText: String,
        moodTag: String?,
        moodNote: String?,
        nowMillis: Long,
    )

    suspend fun updateRecord(record: NoteRecord)

    suspend fun deleteRecord(record: NoteRecord)

    suspend fun importRecords(records: List<NoteRecord>)
}

data class RecordTimelineUiState(
    val eventText: String = "",
    val selectedMood: String? = null,
    val moodNote: String = "",
    val editEventText: String = "",
    val editSelectedMood: String? = null,
    val editMoodNote: String = "",
    val editingRecord: NoteRecord? = null,
    val timelineDays: List<TimelineDay> = emptyList(),
    val message: String? = null,
) {
    val isEditing: Boolean = editingRecord != null
}

data class TimelineDay(
    val label: String,
    val records: List<NoteRecord>,
)

class RecordTimelineViewModel(
    private val repository: RecordOperations,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val formState = MutableStateFlow(RecordTimelineUiState())

    val uiState: StateFlow<RecordTimelineUiState> = combine(
        formState,
        repository.observeRecords(),
    ) { state, records ->
        state.copy(timelineDays = records.toTimelineDays(clock))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RecordTimelineUiState())

    fun updateEventText(value: String) {
        formState.update { it.copy(eventText = value, message = null) }
    }

    fun selectMood(value: String?) {
        formState.update { it.copy(selectedMood = value, message = null) }
    }

    fun updateMoodNote(value: String) {
        formState.update { it.copy(moodNote = value, message = null) }
    }

    fun updateEditEventText(value: String) {
        formState.update { it.copy(editEventText = value, message = null) }
    }

    fun selectEditMood(value: String?) {
        formState.update { it.copy(editSelectedMood = value, message = null) }
    }

    fun updateEditMoodNote(value: String) {
        formState.update { it.copy(editMoodNote = value, message = null) }
    }

    fun save() {
        val state = formState.value
        val isEditing = state.editingRecord != null
        val eventText = (if (isEditing) state.editEventText else state.eventText).trim()
        val selectedMood = if (isEditing) state.editSelectedMood else state.selectedMood
        val rawMoodNote = if (isEditing) state.editMoodNote else state.moodNote

        if (eventText.isEmpty()) {
            formState.update { it.copy(message = "未记今日之事，不宜入录") }
            return
        }

        viewModelScope.launch {
            runCatching {
                val moodNote = rawMoodNote.trim().ifEmpty { null }
                val now = clock.millis()
                val editing = state.editingRecord
                if (editing == null) {
                    repository.addRecord(eventText, selectedMood, moodNote, now)
                } else {
                    repository.updateRecord(
                        editing.copy(
                            eventText = eventText,
                            moodTag = selectedMood,
                            moodNote = moodNote,
                            updatedAt = now,
                        )
                    )
                }
            }.onSuccess {
                if (isEditing) {
                    formState.update { it.copy(editEventText = "", editSelectedMood = null, editMoodNote = "", editingRecord = null) }
                } else {
                    formState.update { it.copy(eventText = "", selectedMood = null, moodNote = "") }
                }
            }.onFailure {
                formState.update { it.copy(message = "入录未成，烦请再试") }
            }
        }
    }

    fun startEditing(record: NoteRecord) {
        formState.update { state ->
            state.copy(
                editEventText = record.eventText,
                editSelectedMood = record.moodTag,
                editMoodNote = record.moodNote.orEmpty(),
                editingRecord = record,
            )
        }
    }

    fun cancelEditing() {
        formState.update { it.copy(editEventText = "", editSelectedMood = null, editMoodNote = "", editingRecord = null) }
    }

    fun delete(record: NoteRecord) {
        viewModelScope.launch {
            runCatching { repository.deleteRecord(record) }
                .onSuccess {
                    formState.update { state ->
                        if (state.editingRecord?.id == record.id) {
                            state.copy(editEventText = "", editSelectedMood = null, editMoodNote = "", editingRecord = null)
                        } else state
                    }
                }
                .onFailure {
                    formState.update { state -> state.copy(message = "抹去未成，烦请再试") }
                }
        }
    }

    fun clearMessage() {
        formState.update { it.copy(message = null) }
    }
}

class RecordTimelineViewModelFactory(
    private val repository: RecordOperations,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RecordTimelineViewModel(repository) as T
    }
}

private fun List<NoteRecord>.toTimelineDays(clock: Clock): List<TimelineDay> {
    val today = LocalDate.now(clock)
    val yesterday = today.minusDays(1)
    return groupBy { record ->
        Instant.ofEpochMilli(record.createdAt).atZone(clock.zone).toLocalDate()
    }.map { (date, records) ->
        TimelineDay(
            label = when (date) {
                today -> "今日"
                yesterday -> "昨日"
                else -> date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            },
            records = records,
        )
    }
}
