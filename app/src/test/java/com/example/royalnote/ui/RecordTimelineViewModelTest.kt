package com.example.royalnote.ui

import com.example.royalnote.data.NoteRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class RecordTimelineViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeRecordRepository
    private lateinit var clock: Clock

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeRecordRepository()
        clock = Clock.fixed(Instant.parse("2026-06-28T10:15:30Z"), ZoneId.of("Asia/Shanghai"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveRejectsBlankEventText() = runTest {
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.updateEventText("   ")
        viewModel.save()
        advanceUntilIdle()

        assertEquals("未记今日之事，不宜入录", viewModel.uiState.value.message)
        assertTrue(repository.records.value.isEmpty())
    }

    @Test
    fun saveAddsRecordAndClearsForm() = runTest {
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.updateEventText("整理书桌")
        viewModel.selectMood("平静")
        viewModel.updateMoodNote("心里安稳了一点")
        viewModel.save()
        advanceUntilIdle()

        val record = repository.records.value.single()
        assertEquals("整理书桌", record.eventText)
        assertEquals("平静", record.moodTag)
        assertEquals("心里安稳了一点", record.moodNote)
        assertEquals(clock.millis(), record.createdAt)
        assertEquals("", viewModel.uiState.value.eventText)
        assertNull(viewModel.uiState.value.selectedMood)
        assertEquals("", viewModel.uiState.value.moodNote)
    }

    @Test
    fun editUpdatesRecordWithoutChangingCreatedAt() = runTest {
        val existing = NoteRecord(1, "散步", "疲惫", null, 1000L, 1000L)
        repository.records.value = listOf(existing)
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.startEditing(existing)
        viewModel.updateEditEventText("散步二十分钟")
        viewModel.selectEditMood(null)
        viewModel.updateEditMoodNote("比出门前轻松")
        viewModel.save()
        advanceUntilIdle()

        val updated = repository.records.value.single()
        assertEquals(1L, updated.id)
        assertEquals("散步二十分钟", updated.eventText)
        assertNull(updated.moodTag)
        assertEquals("比出门前轻松", updated.moodNote)
        assertEquals(1000L, updated.createdAt)
        assertEquals(clock.millis(), updated.updatedAt)
        assertFalse(viewModel.uiState.value.isEditing)
    }

    @Test
    fun deleteRemovesRecord() = runTest {
        val record = NoteRecord(1, "喝水", null, null, 1000L, 1000L)
        repository.records.value = listOf(record)
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.delete(record)
        advanceUntilIdle()

        assertTrue(repository.records.value.isEmpty())
    }

    @Test
    fun deleteEditingRecordClearsOnlyEditState() = runTest {
        val record = NoteRecord(1, "喝水", "平静", "刚倒了一杯水", 1000L, 1000L)
        repository.records.value = listOf(record)
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.updateEventText("写日记")
        viewModel.updateMoodNote("今日安稳")
        viewModel.startEditing(record)
        viewModel.delete(record)
        advanceUntilIdle()

        assertTrue(repository.records.value.isEmpty())
        val state = viewModel.uiState.value
        assertFalse(state.isEditing)
        assertEquals("", state.editEventText)
        assertNull(state.editSelectedMood)
        assertEquals("", state.editMoodNote)
        assertNull(state.editingRecord)
        assertEquals("写日记", state.eventText)
        assertEquals("今日安稳", state.moodNote)
    }

    @Test
    fun startEditingDoesNotOverwriteNewFormDraft() = runTest {
        val existing = NoteRecord(1, "散步", "疲惫", null, 1000L, 1000L)
        repository.records.value = listOf(existing)
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.updateEventText("写日记")
        viewModel.selectMood("平静")
        viewModel.updateMoodNote("今日安稳")
        viewModel.startEditing(existing)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("写日记", state.eventText)
        assertEquals("平静", state.selectedMood)
        assertEquals("今日安稳", state.moodNote)

        assertEquals("散步", state.editEventText)
        assertEquals("疲惫", state.editSelectedMood)
    }

    @Test
    fun saveEditUsesEditFieldsAndClearsOnlyEditState() = runTest {
        val existing = NoteRecord(1, "散步", "疲惫", null, 1000L, 1000L)
        repository.records.value = listOf(existing)
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.updateEventText("写日记")
        viewModel.updateMoodNote("今日安稳")

        viewModel.startEditing(existing)
        viewModel.updateEditEventText("散步二十分钟")
        viewModel.selectEditMood(null)
        viewModel.updateEditMoodNote("比出门前轻松")
        viewModel.save()
        advanceUntilIdle()

        val updated = repository.records.value.single()
        assertEquals(1L, updated.id)
        assertEquals("散步二十分钟", updated.eventText)
        assertNull(updated.moodTag)
        assertEquals("比出门前轻松", updated.moodNote)
        assertEquals(1000L, updated.createdAt)
        assertEquals(clock.millis(), updated.updatedAt)

        assertFalse(viewModel.uiState.value.isEditing)
        assertEquals("", viewModel.uiState.value.editEventText)
        assertNull(viewModel.uiState.value.editSelectedMood)
        assertEquals("", viewModel.uiState.value.editMoodNote)

        assertEquals("写日记", viewModel.uiState.value.eventText)
        assertEquals("今日安稳", viewModel.uiState.value.moodNote)
    }

    @Test
    fun cancelEditingClearsOnlyEditState() = runTest {
        val existing = NoteRecord(1, "散步", "疲惫", null, 1000L, 1000L)
        repository.records.value = listOf(existing)
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.updateEventText("写日记")
        viewModel.updateMoodNote("今日安稳")
        viewModel.startEditing(existing)
        viewModel.updateEditEventText("散步二十分钟")
        viewModel.cancelEditing()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isEditing)
        assertEquals("", viewModel.uiState.value.editEventText)
        assertNull(viewModel.uiState.value.editSelectedMood)
        assertEquals("", viewModel.uiState.value.editMoodNote)

        assertEquals("写日记", viewModel.uiState.value.eventText)
        assertEquals("今日安稳", viewModel.uiState.value.moodNote)
    }

    @Test
    fun groupsRecordsByTodayYesterdayAndDate() = runTest {
        repository.records.value = listOf(
            NoteRecord(1, "今日", null, null, Instant.parse("2026-06-28T01:00:00Z").toEpochMilli(), 1L),
            NoteRecord(2, "昨日", null, null, Instant.parse("2026-06-27T01:00:00Z").toEpochMilli(), 1L),
            NoteRecord(3, "更早", null, null, Instant.parse("2026-06-24T01:00:00Z").toEpochMilli(), 1L),
        )
        val viewModel = RecordTimelineViewModel(repository, clock)
        advanceUntilIdle()

        val labels = viewModel.uiState.first().timelineDays.map { it.label }
        assertEquals(listOf("今日", "昨日", "2026-06-24"), labels)
    }
}

private class FakeRecordRepository : RecordOperations {
    val records = MutableStateFlow<List<NoteRecord>>(emptyList())
    private var nextId = 1L

    override fun observeRecords(): Flow<List<NoteRecord>> = records

    override suspend fun addRecord(
        eventText: String,
        moodTag: String?,
        moodNote: String?,
        nowMillis: Long,
    ) {
        records.value = records.value + NoteRecord(nextId++, eventText, moodTag, moodNote, nowMillis, nowMillis)
    }

    override suspend fun updateRecord(record: NoteRecord) {
        records.value = records.value.map { if (it.id == record.id) record else it }
    }

    override suspend fun deleteRecord(record: NoteRecord) {
        records.value = records.value.filterNot { it.id == record.id }
    }
}
