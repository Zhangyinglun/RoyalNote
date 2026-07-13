package com.example.royalnote.ui

import com.example.royalnote.data.NoteRecord
import com.example.royalnote.network.MissingOpenRouterApiKeyException
import com.example.royalnote.network.ParsedRecord
import com.example.royalnote.network.ParsedRecords
import com.example.royalnote.network.RecordParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(
        Instant.parse("2026-06-21T02:00:00Z"),
        ZoneId.of("Asia/Shanghai"),
    )
    private lateinit var parser: FakeRecordParser
    private lateinit var repository: FakeImportRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        parser = FakeRecordParser()
        repository = FakeImportRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun importEmptyTextShowsMessage() = runTest {
        val viewModel = ImportViewModel(parser, repository, clock)

        viewModel.updateText("   ")
        viewModel.importRecords()
        advanceUntilIdle()

        assertEquals("先粘贴旧日记录", viewModel.uiState.value.message)
        assertFalse(parser.wasCalled)
    }

    @Test
    fun importSuccessInsertsRecordsAndShowsCount() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("开会", "疲惫", "太长了", "2026-06-20T10:30:00", "2026-06-20T10:30:00"),
            ParsedRecord("写文档", "满足", null, "2026-06-20T15:00:00", "2026-06-20T15:00:00"),
        ))
        val viewModel = ImportViewModel(parser, repository, clock)

        viewModel.updateText("今天开了个会，然后写了文档")
        viewModel.importRecords()
        advanceUntilIdle()

        assertEquals(2, repository.importedRecords.size)
        assertNull(viewModel.uiState.value.message)
        assertEquals("已入录 2 则", viewModel.uiState.value.successDialogMessage)
        assertTrue(viewModel.uiState.value.isSuccess)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun repeatedImportWhileParserIsPendingRunsOneOperation() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("开会", null, null, "2026-06-20T10:30:00", "2026-06-20T10:30:00"),
        ))
        parser.pause()
        val viewModel = ImportViewModel(parser, repository, clock)
        viewModel.updateText("10:30 开会")

        viewModel.importRecords()
        runCurrent()
        viewModel.importRecords()
        runCurrent()

        assertEquals(1, parser.calls)
        assertEquals(0, repository.importCalls)
        parser.resume()
        advanceUntilIdle()
        assertEquals(1, repository.importCalls)
        assertEquals(1, repository.importedRecords.size)
        assertEquals("已入录 1 则", viewModel.uiState.value.successDialogMessage)
    }

    @Test
    fun repeatedImportWhileRepositoryIsPendingRunsOneOperation() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("开会", null, null, "2026-06-20T10:30:00", "2026-06-20T10:30:00"),
        ))
        repository.pause()
        val viewModel = ImportViewModel(parser, repository, clock)
        viewModel.updateText("10:30 开会")

        viewModel.importRecords()
        runCurrent()
        viewModel.importRecords()
        runCurrent()

        assertEquals(1, parser.calls)
        assertEquals(1, repository.importCalls)
        repository.resume()
        advanceUntilIdle()
        assertEquals(1, repository.importedRecords.size)
        assertEquals("已入录 1 则", viewModel.uiState.value.successDialogMessage)
    }

    @Test
    fun resetDuringDelayedParserLetsNewImportOwnFinalState() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("旧记录", null, null, "2026-06-20T09:00:00", "2026-06-20T09:00:00"),
        ))
        parser.pause()
        val viewModel = ImportViewModel(parser, repository, clock)
        viewModel.updateText("旧文本")
        viewModel.importRecords()
        runCurrent()

        viewModel.resetState()
        parser.result = ParsedRecords(listOf(
            ParsedRecord("新记录", null, null, "2026-06-20T10:00:00", "2026-06-20T10:00:00"),
        ))
        viewModel.updateText("新文本")
        viewModel.importRecords()
        runCurrent()

        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals(2, parser.calls)
        parser.resume()
        advanceUntilIdle()

        assertEquals(1, repository.importCalls)
        assertEquals(listOf("新记录"), repository.importedRecords.map { it.eventText })
        assertEquals("新文本", viewModel.uiState.value.text)
        assertEquals("已入录 1 则", viewModel.uiState.value.successDialogMessage)
        assertTrue(viewModel.uiState.value.isSuccess)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun resetDuringDelayedPersistenceLetsNewImportOwnFinalState() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("旧记录一", null, null, "2026-06-20T09:00:00", "2026-06-20T09:00:00"),
            ParsedRecord("旧记录二", null, null, "2026-06-20T09:30:00", "2026-06-20T09:30:00"),
        ))
        repository.pause()
        val viewModel = ImportViewModel(parser, repository, clock)
        viewModel.updateText("旧文本")
        viewModel.importRecords()
        runCurrent()

        viewModel.resetState()
        parser.result = ParsedRecords(listOf(
            ParsedRecord("新记录", null, null, "2026-06-20T10:00:00", "2026-06-20T10:00:00"),
        ))
        viewModel.updateText("新文本")
        viewModel.importRecords()
        runCurrent()

        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals(2, parser.calls)
        assertEquals(2, repository.importCalls)
        repository.resume()
        advanceUntilIdle()

        assertEquals(listOf("新记录"), repository.importedRecords.map { it.eventText })
        assertEquals("新文本", viewModel.uiState.value.text)
        assertEquals("已入录 1 则", viewModel.uiState.value.successDialogMessage)
        assertTrue(viewModel.uiState.value.isSuccess)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun importStoresRangeAndUsesImportTimeForMetadata() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("开会", "疲惫", null, "2026-06-20T10:30:00", "2026-06-20T11:45:00"),
        ))
        val viewModel = ImportViewModel(parser, repository, clock)

        viewModel.updateText("10:30 到 11:45 开会")
        viewModel.importRecords()
        advanceUntilIdle()

        val record = repository.importedRecords.single()
        assertEquals(
            LocalDateTime.parse("2026-06-20T10:30:00").atZone(clock.zone).toInstant().toEpochMilli(),
            record.startedAt,
        )
        assertEquals(
            LocalDateTime.parse("2026-06-20T11:45:00").atZone(clock.zone).toInstant().toEpochMilli(),
            record.endedAt,
        )
        assertTrue(record.endedAt > record.startedAt)
        assertEquals(clock.millis(), record.createdAt)
        assertEquals(clock.millis(), record.updatedAt)
    }

    @Test
    fun importSingleTimeStoresZeroMinuteRange() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("开会", "疲惫", null, "2026-06-20T10:30:00", "2026-06-20T10:30:00"),
        ))
        val viewModel = ImportViewModel(parser, repository, clock)

        viewModel.updateText("10:30 开会")
        viewModel.importRecords()
        advanceUntilIdle()

        val record = repository.importedRecords.single()
        assertEquals(record.startedAt, record.endedAt)
    }

    @Test
    fun importSuccessShowsDialogMessageUntilDismissed() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("开会", "疲惫", null, "2026-06-20T10:30:00", "2026-06-20T10:30:00"),
        ))
        val viewModel = ImportViewModel(parser, repository, clock)

        viewModel.updateText("今天开会")
        viewModel.importRecords()
        advanceUntilIdle()

        assertEquals("已入录 1 则", viewModel.uiState.value.successDialogMessage)

        viewModel.dismissSuccessDialog()

        assertNull(viewModel.uiState.value.successDialogMessage)
    }

    @Test
    fun importNetworkErrorShowsMessage() = runTest {
        parser.exception = IOException("network error")
        val viewModel = ImportViewModel(parser, repository, clock)

        viewModel.updateText("一些文字")
        viewModel.importRecords()
        advanceUntilIdle()

        assertEquals("网络不通，稍后再试", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun missingApiKeyShowsSettingsMessage() = runTest {
        parser.exception = MissingOpenRouterApiKeyException()
        val viewModel = ImportViewModel(parser, repository, clock)

        viewModel.updateText("一些文字")
        viewModel.importRecords()
        advanceUntilIdle()

        assertEquals("请先在设置中填写 OpenRouter API Key", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun importParseErrorShowsMessage() = runTest {
        parser.exception = RuntimeException("parse error")
        val viewModel = ImportViewModel(parser, repository, clock)

        viewModel.updateText("一些文字")
        viewModel.importRecords()
        advanceUntilIdle()

        assertEquals("解析未成，稍后再试", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun importPersistenceErrorShowsDistinctMessage() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("开会", null, null, "2026-06-20T10:30:00", "2026-06-20T10:30:00"),
        ))
        repository.exception = IllegalStateException("database full")
        val viewModel = ImportViewModel(parser, repository, clock)

        viewModel.updateText("10:30 开会")
        viewModel.importRecords()
        advanceUntilIdle()

        assertEquals("记录已解析，但保存失败，请稍后再试", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun parserCancellationDoesNotShowFailureMessage() = runTest {
        parser.exception = CancellationException("cancel parse")
        val viewModel = ImportViewModel(parser, repository, clock)

        viewModel.updateText("一些文字")
        viewModel.importRecords()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isSuccess)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun persistenceCancellationDoesNotShowFailureMessage() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("开会", null, null, "2026-06-20T10:30:00", "2026-06-20T10:30:00"),
        ))
        repository.exception = CancellationException("cancel persistence")
        val viewModel = ImportViewModel(parser, repository, clock)

        viewModel.updateText("10:30 开会")
        viewModel.importRecords()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isSuccess)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun importFiltersInvalidMoodTag() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("事件A", "乱七八糟", null, "2026-06-20T10:00:00", "2026-06-20T10:00:00"),
        ))
        val viewModel = ImportViewModel(parser, repository, clock)

        viewModel.updateText("事件A")
        viewModel.importRecords()
        advanceUntilIdle()

        val record = repository.importedRecords.single()
        assertNull(record.moodTag)
    }

    @Test
    fun importTimestampParseFailureFallsBackAllTimesToImportTime() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("事件", null, null, "not-a-date", "2026-06-20T10:00:00"),
        ))
        val viewModel = ImportViewModel(parser, repository, clock)

        viewModel.updateText("事件")
        viewModel.importRecords()
        advanceUntilIdle()

        val record = repository.importedRecords.single()
        assertEquals(clock.millis(), record.startedAt)
        assertEquals(clock.millis(), record.endedAt)
        assertEquals(clock.millis(), record.createdAt)
        assertEquals(clock.millis(), record.updatedAt)
    }

    @Test
    fun importReversedRangeFallsBackAllTimesToImportTime() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("事件", null, null, "2026-06-20T11:00:00", "2026-06-20T10:00:00"),
        ))
        val viewModel = ImportViewModel(parser, repository, clock)

        viewModel.updateText("11:00 到 10:00 事件")
        viewModel.importRecords()
        advanceUntilIdle()

        val record = repository.importedRecords.single()
        assertEquals(clock.millis(), record.startedAt)
        assertEquals(clock.millis(), record.endedAt)
        assertEquals(clock.millis(), record.createdAt)
        assertEquals(clock.millis(), record.updatedAt)
    }

    @Test
    fun updateTextClearsMessage() = runTest {
        val viewModel = ImportViewModel(parser, repository, clock)

        viewModel.updateText("   ")
        viewModel.importRecords()
        advanceUntilIdle()
        assertEquals("先粘贴旧日记录", viewModel.uiState.value.message)

        viewModel.updateText("一些文字")
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun resetStateClearsEverything() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("事件", null, null, "2026-06-20T10:00:00", "2026-06-20T10:00:00"),
        ))
        val viewModel = ImportViewModel(parser, repository, clock)

        viewModel.updateText("旧文字")
        viewModel.importRecords()
        advanceUntilIdle()

        viewModel.resetState()

        assertEquals("", viewModel.uiState.value.text)
        assertNull(viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isSuccess)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}

private class FakeRecordParser : RecordParser {
    var result: ParsedRecords = ParsedRecords(emptyList())
    var exception: Throwable? = null
    var wasCalled = false
    var calls = 0
        private set
    private var gate: CompletableDeferred<Unit>? = null

    fun pause() {
        gate = CompletableDeferred()
    }

    fun resume() {
        check(gate?.complete(Unit) == true)
        gate = null
    }

    override suspend fun parseRecords(text: String): ParsedRecords {
        wasCalled = true
        calls++
        val response = result
        gate?.await()
        exception?.let { throw it }
        return response
    }
}

private class FakeImportRepository : RecordOperations {
    val importedRecords = mutableListOf<NoteRecord>()
    var exception: Throwable? = null
    var importCalls = 0
        private set
    private var gate: CompletableDeferred<Unit>? = null
    private val records = MutableStateFlow<List<NoteRecord>>(emptyList())

    fun pause() {
        gate = CompletableDeferred()
    }

    fun resume() {
        check(gate?.complete(Unit) == true)
        gate = null
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
        records.value = records.value + NoteRecord(
            id = 0,
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
        records.value = records.value.map { if (it.id == record.id) record else it }
    }

    override suspend fun deleteRecord(record: NoteRecord) {
        records.value = records.value.filterNot { it.id == record.id }
    }

    override suspend fun importRecords(records: List<NoteRecord>) {
        importCalls++
        gate?.await()
        exception?.let { throw it }
        importedRecords.addAll(records)
    }
}
