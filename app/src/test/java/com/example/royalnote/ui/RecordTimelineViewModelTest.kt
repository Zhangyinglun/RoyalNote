package com.example.royalnote.ui

import com.example.royalnote.data.NoteRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
    fun newFormStartsAsZeroMinuteRangeAtCurrentTime() {
        val state = RecordTimelineViewModel(repository, clock).uiState.value

        assertEquals(clock.millis(), state.startedAt)
        assertEquals(clock.millis(), state.endedAt)
    }

    @Test
    fun movingStartPastEndMovesEndToStart() {
        val viewModel = RecordTimelineViewModel(repository, clock)
        val later = clock.millis() + 60_000L

        viewModel.updateStartedAt(later)

        assertEquals(later, viewModel.uiState.value.startedAt)
        assertEquals(later, viewModel.uiState.value.endedAt)
    }

    @Test
    fun movingEditStartPastEndMovesEditEndToStart() {
        val existing = record(startedAt = 1_000L, endedAt = 2_000L)
        val viewModel = RecordTimelineViewModel(repository, clock)
        viewModel.startEditing(existing)

        viewModel.updateEditStartedAt(3_000L)

        assertEquals(3_000L, viewModel.uiState.value.editStartedAt)
        assertEquals(3_000L, viewModel.uiState.value.editEndedAt)
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
    fun saveRejectsEndBeforeStart() = runTest {
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.updateEventText("夜读")
        viewModel.updateEndedAt(clock.millis() - 60_000L)
        viewModel.save()
        advanceUntilIdle()

        assertEquals("结束时间不可早于开始时间", viewModel.uiState.value.message)
        assertTrue(repository.records.value.isEmpty())
    }

    @Test
    fun saveRejectsEditEndBeforeStart() = runTest {
        val existing = record(startedAt = 1_000L, endedAt = 2_000L)
        repository.records.value = listOf(existing)
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.startEditing(existing)
        viewModel.updateEditEndedAt(999L)
        viewModel.save()
        advanceUntilIdle()

        assertEquals("结束时间不可早于开始时间", viewModel.uiState.value.message)
        assertEquals(existing, repository.records.value.single())
    }

    @Test
    fun saveAcceptsZeroMinuteRange() = runTest {
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.updateEventText("静坐")
        viewModel.save()
        advanceUntilIdle()

        val saved = repository.records.value.single()
        assertEquals(clock.millis(), saved.startedAt)
        assertEquals(clock.millis(), saved.endedAt)
    }

    @Test
    fun saveAddsRecordAndClearsForm() = runTest {
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.updateEventText("整理书桌")
        viewModel.selectMood("平静")
        viewModel.updateMoodNote("心里安稳了一点")
        viewModel.updateStartedAt(clock.millis() - 3_600_000L)
        viewModel.updateEndedAt(clock.millis() - 1_800_000L)
        viewModel.save()
        advanceUntilIdle()

        val record = repository.records.value.single()
        assertEquals("整理书桌", record.eventText)
        assertEquals("平静", record.moodTag)
        assertEquals("心里安稳了一点", record.moodNote)
        assertEquals(clock.millis() - 3_600_000L, record.startedAt)
        assertEquals(clock.millis() - 1_800_000L, record.endedAt)
        assertEquals(clock.millis(), record.createdAt)
        assertEquals("", viewModel.uiState.value.eventText)
        assertNull(viewModel.uiState.value.selectedMood)
        assertEquals("", viewModel.uiState.value.moodNote)
        assertEquals(clock.millis(), viewModel.uiState.value.startedAt)
        assertEquals(clock.millis(), viewModel.uiState.value.endedAt)
    }

    @Test
    fun repeatedCreateSaveWhilePersistenceIsPendingWritesOnce() = runTest {
        repository.pausePersistence()
        val viewModel = RecordTimelineViewModel(repository, clock)
        viewModel.updateEventText("整理书桌")

        viewModel.save()
        runCurrent()
        assertTrue(viewModel.uiState.value.isSaving)
        viewModel.updateEventText("")
        viewModel.save()
        runCurrent()

        assertEquals(1, repository.addCalls)
        assertNull(viewModel.uiState.value.message)
        repository.resumePersistence()
        advanceUntilIdle()
        assertEquals(1, repository.records.value.size)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun repeatedEditSaveWhilePersistenceIsPendingWritesOnce() = runTest {
        val existing = record(startedAt = 1_000L, endedAt = 2_000L)
        repository.records.value = listOf(existing)
        repository.pausePersistence()
        val viewModel = RecordTimelineViewModel(repository, clock)
        viewModel.startEditing(existing)
        viewModel.updateEditEventText("散步二十分钟")

        viewModel.save()
        runCurrent()
        assertTrue(viewModel.uiState.value.isSaving)
        viewModel.save()
        runCurrent()

        assertEquals(1, repository.updateCalls)
        repository.resumePersistence()
        advanceUntilIdle()
        assertEquals("散步二十分钟", repository.records.value.single().eventText)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun editOpenedWhileCreateIsPendingSurvivesCreateReset() = runTest {
        val existing = record(startedAt = 1_000L, endedAt = 2_000L)
        repository.records.value = listOf(existing)
        repository.pausePersistence()
        val viewModel = RecordTimelineViewModel(repository, clock)
        viewModel.updateEventText("整理书桌")

        viewModel.save()
        runCurrent()
        viewModel.startEditing(existing)
        repository.resumePersistence()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.eventText)
        assertEquals(clock.millis(), state.startedAt)
        assertEquals(clock.millis(), state.endedAt)
        assertEquals(existing, state.editingRecord)
        assertEquals(existing.eventText, state.editEventText)
        assertEquals(existing.startedAt, state.editStartedAt)
        assertEquals(existing.endedAt, state.editEndedAt)
    }

    @Test
    fun newDraftTypedWhileCreateIsSuspendedSurvivesCompletion() = runTest {
        repository.pausePersistence()
        val viewModel = RecordTimelineViewModel(repository, clock)
        viewModel.updateEventText("整理书桌")
        viewModel.selectMood("平静")
        viewModel.updateMoodNote("旧注")
        viewModel.updateStartedAt(1_000L)
        viewModel.updateEndedAt(2_000L)

        viewModel.save()
        runCurrent()
        viewModel.updateEventText("写新札记")
        viewModel.selectMood("开心")
        viewModel.updateMoodNote("新注")
        viewModel.updateStartedAt(3_000L)
        viewModel.updateEndedAt(4_000L)
        repository.resumePersistence()
        advanceUntilIdle()

        val saved = repository.records.value.single()
        assertEquals("整理书桌", saved.eventText)
        assertEquals(1_000L, saved.startedAt)
        assertEquals(2_000L, saved.endedAt)
        val state = viewModel.uiState.value
        assertEquals("写新札记", state.eventText)
        assertEquals("开心", state.selectedMood)
        assertEquals("新注", state.moodNote)
        assertEquals(3_000L, state.startedAt)
        assertEquals(4_000L, state.endedAt)
    }

    @Test
    fun editUpdatesRecordWithoutChangingCreatedAt() = runTest {
        val existing = NoteRecord(
            id = 1,
            eventText = "散步",
            moodTag = "疲惫",
            moodNote = null,
            startedAt = 1000L,
            endedAt = 1000L,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        repository.records.value = listOf(existing)
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.startEditing(existing)
        viewModel.updateEditEventText("散步二十分钟")
        viewModel.selectEditMood(null)
        viewModel.updateEditMoodNote("比出门前轻松")
        viewModel.updateEditStartedAt(2_000L)
        viewModel.updateEditEndedAt(3_000L)
        viewModel.save()
        advanceUntilIdle()

        val updated = repository.records.value.single()
        assertEquals(1L, updated.id)
        assertEquals("散步二十分钟", updated.eventText)
        assertNull(updated.moodTag)
        assertEquals("比出门前轻松", updated.moodNote)
        assertEquals(2_000L, updated.startedAt)
        assertEquals(3_000L, updated.endedAt)
        assertEquals(1000L, updated.createdAt)
        assertEquals(clock.millis(), updated.updatedAt)
        assertFalse(viewModel.uiState.value.isEditing)
    }

    @Test
    fun editOpenedWhileAnotherEditIsSuspendedSurvivesCompletion() = runTest {
        val editA = record(startedAt = 1_000L, endedAt = 2_000L)
        val editB = record(startedAt = 4_000L, endedAt = 5_000L).copy(id = 2, eventText = "读书")
        repository.records.value = listOf(editA, editB)
        repository.pausePersistence()
        val viewModel = RecordTimelineViewModel(repository, clock)
        viewModel.startEditing(editA)
        viewModel.updateEditEventText("散步二十分钟")
        viewModel.updateEditStartedAt(2_000L)
        viewModel.updateEditEndedAt(3_000L)

        viewModel.save()
        runCurrent()
        viewModel.startEditing(editB)
        repository.resumePersistence()
        advanceUntilIdle()

        assertEquals("散步二十分钟", repository.records.value.first { it.id == editA.id }.eventText)
        val state = viewModel.uiState.value
        assertEquals(editB, state.editingRecord)
        assertEquals(editB.eventText, state.editEventText)
        assertEquals(editB.moodTag, state.editSelectedMood)
        assertEquals(editB.moodNote.orEmpty(), state.editMoodNote)
        assertEquals(editB.startedAt, state.editStartedAt)
        assertEquals(editB.endedAt, state.editEndedAt)
    }

    @Test
    fun persistenceFailurePreservesAllActiveFormFieldsAndTimes() = runTest {
        val existing = record(startedAt = 1_000L, endedAt = 2_000L)
        repository.records.value = listOf(existing)
        repository.failPersistence()
        val viewModel = RecordTimelineViewModel(repository, clock)
        viewModel.updateEventText("新表单")
        viewModel.selectMood("开心")
        viewModel.updateMoodNote("新表单注")
        viewModel.updateStartedAt(10_000L)
        viewModel.updateEndedAt(11_000L)
        viewModel.startEditing(existing)
        viewModel.updateEditEventText("编辑表单")
        viewModel.selectEditMood("平静")
        viewModel.updateEditMoodNote("编辑表单注")
        viewModel.updateEditStartedAt(3_000L)
        viewModel.updateEditEndedAt(4_000L)

        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("新表单", state.eventText)
        assertEquals("开心", state.selectedMood)
        assertEquals("新表单注", state.moodNote)
        assertEquals(10_000L, state.startedAt)
        assertEquals(11_000L, state.endedAt)
        assertEquals("编辑表单", state.editEventText)
        assertEquals("平静", state.editSelectedMood)
        assertEquals("编辑表单注", state.editMoodNote)
        assertEquals(3_000L, state.editStartedAt)
        assertEquals(4_000L, state.editEndedAt)
        assertEquals(existing, state.editingRecord)
        assertEquals("入录未成，烦请再试", state.message)
        assertEquals(existing, repository.records.value.single())
    }

    @Test
    fun saveCancellationDoesNotShowFailureMessage() = runTest {
        repository.failPersistence(CancellationException("cancel save"))
        val viewModel = RecordTimelineViewModel(repository, clock)
        viewModel.updateEventText("整理书桌")

        viewModel.save()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(repository.records.value.isEmpty())
    }

    @Test
    fun deleteRemovesRecord() = runTest {
        val record = NoteRecord(
            id = 1,
            eventText = "喝水",
            moodTag = null,
            moodNote = null,
            startedAt = 1000L,
            endedAt = 1000L,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        repository.records.value = listOf(record)
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.delete(record)
        advanceUntilIdle()

        assertTrue(repository.records.value.isEmpty())
    }

    @Test
    fun deleteCancellationDoesNotShowFailureMessage() = runTest {
        val existing = record(startedAt = 1_000L, endedAt = 2_000L)
        repository.records.value = listOf(existing)
        repository.failPersistence(CancellationException("cancel delete"))
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.delete(existing)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.message)
        assertEquals(listOf(existing), repository.records.value)
    }

    @Test
    fun deleteEditingRecordClearsOnlyEditState() = runTest {
        val record = NoteRecord(
            id = 1,
            eventText = "喝水",
            moodTag = "平静",
            moodNote = "刚倒了一杯水",
            startedAt = 1000L,
            endedAt = 1000L,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
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
        assertNull(state.editStartedAt)
        assertNull(state.editEndedAt)
        assertNull(state.editingRecord)
        assertEquals("写日记", state.eventText)
        assertEquals("今日安稳", state.moodNote)
    }

    @Test
    fun startEditingDoesNotOverwriteNewFormDraft() = runTest {
        val existing = NoteRecord(
            id = 1,
            eventText = "散步",
            moodTag = "疲惫",
            moodNote = null,
            startedAt = 1000L,
            endedAt = 1000L,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        repository.records.value = listOf(existing)
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.updateEventText("写日记")
        viewModel.selectMood("平静")
        viewModel.updateMoodNote("今日安稳")
        viewModel.updateStartedAt(clock.millis() - 5_000L)
        viewModel.updateEndedAt(clock.millis() - 4_000L)
        viewModel.startEditing(existing)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("写日记", state.eventText)
        assertEquals("平静", state.selectedMood)
        assertEquals("今日安稳", state.moodNote)
        assertEquals(clock.millis() - 5_000L, state.startedAt)
        assertEquals(clock.millis() - 4_000L, state.endedAt)

        assertEquals("散步", state.editEventText)
        assertEquals("疲惫", state.editSelectedMood)
        assertEquals(existing.startedAt, state.editStartedAt)
        assertEquals(existing.endedAt, state.editEndedAt)
    }

    @Test
    fun saveEditUsesEditFieldsAndClearsOnlyEditState() = runTest {
        val existing = NoteRecord(
            id = 1,
            eventText = "散步",
            moodTag = "疲惫",
            moodNote = null,
            startedAt = 1000L,
            endedAt = 1000L,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        repository.records.value = listOf(existing)
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.updateEventText("写日记")
        viewModel.updateMoodNote("今日安稳")
        viewModel.updateStartedAt(clock.millis() - 5_000L)
        viewModel.updateEndedAt(clock.millis() - 4_000L)

        viewModel.startEditing(existing)
        viewModel.updateEditEventText("散步二十分钟")
        viewModel.selectEditMood(null)
        viewModel.updateEditMoodNote("比出门前轻松")
        viewModel.updateEditStartedAt(2_000L)
        viewModel.updateEditEndedAt(3_000L)
        viewModel.save()
        advanceUntilIdle()

        val updated = repository.records.value.single()
        assertEquals(1L, updated.id)
        assertEquals("散步二十分钟", updated.eventText)
        assertNull(updated.moodTag)
        assertEquals("比出门前轻松", updated.moodNote)
        assertEquals(2_000L, updated.startedAt)
        assertEquals(3_000L, updated.endedAt)
        assertEquals(1000L, updated.createdAt)
        assertEquals(clock.millis(), updated.updatedAt)

        assertFalse(viewModel.uiState.value.isEditing)
        assertEquals("", viewModel.uiState.value.editEventText)
        assertNull(viewModel.uiState.value.editSelectedMood)
        assertEquals("", viewModel.uiState.value.editMoodNote)
        assertNull(viewModel.uiState.value.editStartedAt)
        assertNull(viewModel.uiState.value.editEndedAt)

        assertEquals("写日记", viewModel.uiState.value.eventText)
        assertEquals("今日安稳", viewModel.uiState.value.moodNote)
        assertEquals(clock.millis() - 5_000L, viewModel.uiState.value.startedAt)
        assertEquals(clock.millis() - 4_000L, viewModel.uiState.value.endedAt)
    }

    @Test
    fun cancelEditingClearsOnlyEditState() = runTest {
        val existing = NoteRecord(
            id = 1,
            eventText = "散步",
            moodTag = "疲惫",
            moodNote = null,
            startedAt = 1000L,
            endedAt = 1000L,
            createdAt = 1000L,
            updatedAt = 1000L,
        )
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
        assertNull(viewModel.uiState.value.editStartedAt)
        assertNull(viewModel.uiState.value.editEndedAt)

        assertEquals("写日记", viewModel.uiState.value.eventText)
        assertEquals("今日安稳", viewModel.uiState.value.moodNote)
    }

    @Test
    fun groupsRecordsByTodayYesterdayAndDate() = runTest {
        repository.records.value = listOf(
            NoteRecord(
                id = 1,
                eventText = "今日",
                moodTag = null,
                moodNote = null,
                startedAt = Instant.parse("2026-06-28T01:00:00Z").toEpochMilli(),
                endedAt = Instant.parse("2026-06-29T01:00:00Z").toEpochMilli(),
                createdAt = Instant.parse("2026-06-20T01:00:00Z").toEpochMilli(),
                updatedAt = 1L,
            ),
            NoteRecord(
                id = 2,
                eventText = "昨日",
                moodTag = null,
                moodNote = null,
                startedAt = Instant.parse("2026-06-27T01:00:00Z").toEpochMilli(),
                endedAt = Instant.parse("2026-06-27T01:00:00Z").toEpochMilli(),
                createdAt = Instant.parse("2026-06-28T01:00:00Z").toEpochMilli(),
                updatedAt = 1L,
            ),
            NoteRecord(
                id = 3,
                eventText = "更早",
                moodTag = null,
                moodNote = null,
                startedAt = Instant.parse("2026-06-24T01:00:00Z").toEpochMilli(),
                endedAt = Instant.parse("2026-06-24T01:00:00Z").toEpochMilli(),
                createdAt = Instant.parse("2026-06-28T01:00:00Z").toEpochMilli(),
                updatedAt = 1L,
            ),
        )
        val viewModel = RecordTimelineViewModel(repository, clock)
        advanceUntilIdle()

        val labels = viewModel.uiState.first().timelineDays.map { it.label }
        assertEquals(listOf("今日", "昨日", "2026-06-24"), labels)
        assertEquals(listOf(1L), viewModel.uiState.value.timelineDays.first().records.map { it.id })
    }

    private fun record(startedAt: Long, endedAt: Long) = NoteRecord(
        id = 1,
        eventText = "散步",
        moodTag = "疲惫",
        moodNote = null,
        startedAt = startedAt,
        endedAt = endedAt,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )
}

private class FakeRecordRepository : RecordOperations {
    val records = MutableStateFlow<List<NoteRecord>>(emptyList())
    var addCalls = 0
        private set
    var updateCalls = 0
        private set
    private var nextId = 1L
    private var persistenceGate: CompletableDeferred<Unit>? = null
    private var persistenceFailure: Throwable? = null

    fun pausePersistence() {
        persistenceGate = CompletableDeferred()
    }

    fun resumePersistence() {
        check(persistenceGate?.complete(Unit) == true)
        persistenceGate = null
    }

    fun failPersistence(failure: Throwable = IllegalStateException("Persistence failed")) {
        persistenceFailure = failure
    }

    override fun observeRecords(): Flow<List<NoteRecord>> = records

    override suspend fun addRecord(
        eventText: String,
        moodTag: String?,
        moodNote: String?,
        startedAt: Long,
        endedAt: Long,
        nowMillis: Long,
    ) {
        addCalls++
        beforePersistence()
        records.value = records.value + NoteRecord(
            id = nextId++,
            eventText = eventText,
            moodTag = moodTag,
            moodNote = moodNote,
            startedAt = startedAt,
            endedAt = endedAt,
            createdAt = nowMillis,
            updatedAt = nowMillis,
        )
    }

    override suspend fun updateRecord(record: NoteRecord) {
        updateCalls++
        beforePersistence()
        records.value = records.value.map { if (it.id == record.id) record else it }
    }

    override suspend fun deleteRecord(record: NoteRecord) {
        beforePersistence()
        records.value = records.value.filterNot { it.id == record.id }
    }

    override suspend fun importRecords(records: List<NoteRecord>) {
        val withIds = records.map { it.copy(id = nextId++) }
        this.records.value = this.records.value + withIds
    }

    private suspend fun beforePersistence() {
        persistenceGate?.await()
        persistenceFailure?.let { throw it }
    }
}
