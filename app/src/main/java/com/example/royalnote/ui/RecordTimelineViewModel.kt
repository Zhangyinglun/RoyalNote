package com.example.royalnote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.royalnote.data.NoteRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
        startedAt: Long,
        endedAt: Long,
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
    val startedAt: Long = 0L,
    val endedAt: Long = 0L,
    val editEventText: String = "",
    val editSelectedMood: String? = null,
    val editMoodNote: String = "",
    val editStartedAt: Long? = null,
    val editEndedAt: Long? = null,
    val editingRecord: NoteRecord? = null,
    val timelineDays: List<TimelineDay> = emptyList(),
    val message: String? = null,
    val isSaving: Boolean = false,
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
    private val saveInFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    private val formState = MutableStateFlow(
        clock.millis().let { now ->
            RecordTimelineUiState(startedAt = now, endedAt = now)
        }
    )

    val uiState: StateFlow<RecordTimelineUiState> = formState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeRecords().collect { records ->
                formState.update { it.copy(timelineDays = records.toTimelineDays(clock)) }
            }
        }
    }

    fun updateEventText(value: String) {
        formState.update { it.copy(eventText = value, message = null) }
    }

    fun selectMood(value: String?) {
        formState.update { it.copy(selectedMood = value, message = null) }
    }

    fun updateMoodNote(value: String) {
        formState.update { it.copy(moodNote = value, message = null) }
    }

    fun updateStartedAt(value: Long) {
        formState.update { state ->
            state.copy(startedAt = value, endedAt = maxOf(state.endedAt, value), message = null)
        }
    }

    fun updateEndedAt(value: Long) {
        formState.update { it.copy(endedAt = value, message = null) }
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

    fun updateEditStartedAt(value: Long) {
        formState.update { state ->
            state.copy(
                editStartedAt = value,
                editEndedAt = maxOf(state.editEndedAt ?: value, value),
                message = null,
            )
        }
    }

    fun updateEditEndedAt(value: Long) {
        formState.update { it.copy(editEndedAt = value, message = null) }
    }

    fun save() {
        if (saveInFlight.get()) return

        val state = formState.value
        val editing = state.editingRecord
        val eventText = (if (editing == null) state.eventText else state.editEventText).trim()
        val selectedMood = if (editing == null) state.selectedMood else state.editSelectedMood
        val rawMoodNote = if (editing == null) state.moodNote else state.editMoodNote

        if (eventText.isEmpty()) {
            formState.update { it.copy(message = "未记今日之事，不宜入录") }
            return
        }

        val startedAt = if (editing == null) state.startedAt else state.editStartedAt ?: editing.startedAt
        val endedAt = if (editing == null) state.endedAt else state.editEndedAt ?: editing.endedAt
        if (endedAt < startedAt) {
            formState.update { it.copy(message = "结束时间不可早于开始时间") }
            return
        }
        if (!saveInFlight.compareAndSet(false, true)) return

        formState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                val moodNote = rawMoodNote.trim().ifEmpty { null }
                val now = clock.millis()
                if (editing == null) {
                    repository.addRecord(eventText, selectedMood, moodNote, startedAt, endedAt, now)
                } else {
                    repository.updateRecord(
                        editing.copy(
                            eventText = eventText,
                            moodTag = selectedMood,
                            moodNote = moodNote,
                            startedAt = startedAt,
                            endedAt = endedAt,
                            updatedAt = now,
                        )
                    )
                }

                if (editing == null) {
                    val resetAt = clock.millis()
                    formState.update { current ->
                        if (current.matchesNewForm(state)) {
                            current.copy(
                                eventText = "",
                                selectedMood = null,
                                moodNote = "",
                                startedAt = resetAt,
                                endedAt = resetAt,
                            )
                        } else current
                    }
                } else {
                    formState.update { current ->
                        if (current.matchesEditForm(state)) {
                            current.copy(
                                editEventText = "",
                                editSelectedMood = null,
                                editMoodNote = "",
                                editStartedAt = null,
                                editEndedAt = null,
                                editingRecord = null,
                            )
                        } else current
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                formState.update { it.copy(message = "入录未成，烦请再试") }
            } finally {
                saveInFlight.set(false)
                formState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun startEditing(record: NoteRecord) {
        formState.update { state ->
            state.copy(
                editEventText = record.eventText,
                editSelectedMood = record.moodTag,
                editMoodNote = record.moodNote.orEmpty(),
                editStartedAt = record.startedAt,
                editEndedAt = record.endedAt,
                editingRecord = record,
            )
        }
    }

    fun cancelEditing() {
        formState.update {
            it.copy(
                editEventText = "",
                editSelectedMood = null,
                editMoodNote = "",
                editStartedAt = null,
                editEndedAt = null,
                editingRecord = null,
            )
        }
    }

    fun delete(record: NoteRecord) {
        viewModelScope.launch {
            try {
                repository.deleteRecord(record)
                formState.update { state ->
                    if (state.editingRecord?.id == record.id) {
                        state.copy(
                            editEventText = "",
                            editSelectedMood = null,
                            editMoodNote = "",
                            editStartedAt = null,
                            editEndedAt = null,
                            editingRecord = null,
                        )
                    } else state
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                formState.update { state -> state.copy(message = "抹去未成，烦请再试") }
            }
        }
    }

    fun clearMessage() {
        formState.update { it.copy(message = null) }
    }
}

private fun RecordTimelineUiState.matchesNewForm(submitted: RecordTimelineUiState): Boolean =
    eventText == submitted.eventText &&
        selectedMood == submitted.selectedMood &&
        moodNote == submitted.moodNote &&
        startedAt == submitted.startedAt &&
        endedAt == submitted.endedAt

private fun RecordTimelineUiState.matchesEditForm(submitted: RecordTimelineUiState): Boolean =
    editingRecord == submitted.editingRecord &&
        editEventText == submitted.editEventText &&
        editSelectedMood == submitted.editSelectedMood &&
        editMoodNote == submitted.editMoodNote &&
        editStartedAt == submitted.editStartedAt &&
        editEndedAt == submitted.editEndedAt

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
        Instant.ofEpochMilli(record.startedAt).atZone(clock.zone).toLocalDate()
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
