package com.example.royalnote.ui

import com.example.royalnote.data.NoteRecord
import com.example.royalnote.network.MissingOpenRouterApiKeyException
import com.example.royalnote.network.ParsedRecord
import com.example.royalnote.network.ParsedRecords
import com.example.royalnote.network.RecordParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
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
        val viewModel = ImportViewModel(parser, repository)

        viewModel.updateText("   ")
        viewModel.importRecords()
        advanceUntilIdle()

        assertEquals("先粘贴旧日记录", viewModel.uiState.value.message)
        assertFalse(parser.wasCalled)
    }

    @Test
    fun importSuccessInsertsRecordsAndShowsCount() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("开会", "疲惫", "太长了", "2026-06-20T10:30:00"),
            ParsedRecord("写文档", "满足", null, "2026-06-20T15:00:00"),
        ))
        val viewModel = ImportViewModel(parser, repository)

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
    fun importSuccessShowsDialogMessageUntilDismissed() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("开会", "疲惫", null, "2026-06-20T10:30:00"),
        ))
        val viewModel = ImportViewModel(parser, repository)

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
        val viewModel = ImportViewModel(parser, repository)

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
        val viewModel = ImportViewModel(parser, repository)

        viewModel.updateText("一些文字")
        viewModel.importRecords()
        advanceUntilIdle()

        assertEquals("请先在设置中填写 OpenRouter API Key", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun importParseErrorShowsMessage() = runTest {
        parser.exception = RuntimeException("parse error")
        val viewModel = ImportViewModel(parser, repository)

        viewModel.updateText("一些文字")
        viewModel.importRecords()
        advanceUntilIdle()

        assertEquals("解析未成，稍后再试", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun importFiltersInvalidMoodTag() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("事件A", "乱七八糟", null, "2026-06-20T10:00:00"),
        ))
        val viewModel = ImportViewModel(parser, repository)

        viewModel.updateText("事件A")
        viewModel.importRecords()
        advanceUntilIdle()

        val record = repository.importedRecords.single()
        assertNull(record.moodTag)
    }

    @Test
    fun importTimestampParseFailureFallsBackToNow() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("事件", null, null, "not-a-date"),
        ))
        val viewModel = ImportViewModel(parser, repository)

        viewModel.updateText("事件")
        viewModel.importRecords()
        advanceUntilIdle()

        val record = repository.importedRecords.single()
        assertTrue(record.createdAt > 0)
    }

    @Test
    fun updateTextClearsMessage() = runTest {
        val viewModel = ImportViewModel(parser, repository)

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
            ParsedRecord("事件", null, null, "2026-06-20T10:00:00"),
        ))
        val viewModel = ImportViewModel(parser, repository)

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

    override suspend fun parseRecords(text: String): ParsedRecords {
        wasCalled = true
        exception?.let { throw it }
        return result
    }
}

private class FakeImportRepository : RecordOperations {
    val importedRecords = mutableListOf<NoteRecord>()
    private val records = MutableStateFlow<List<NoteRecord>>(emptyList())

    override fun observeRecords(): Flow<List<NoteRecord>> = records

    override suspend fun addRecord(
        eventText: String,
        moodTag: String?,
        moodNote: String?,
        nowMillis: Long,
    ) {
        records.value = records.value + NoteRecord(0, eventText, moodTag, moodNote, nowMillis, nowMillis)
    }

    override suspend fun updateRecord(record: NoteRecord) {
        records.value = records.value.map { if (it.id == record.id) record else it }
    }

    override suspend fun deleteRecord(record: NoteRecord) {
        records.value = records.value.filterNot { it.id == record.id }
    }

    override suspend fun importRecords(records: List<NoteRecord>) {
        importedRecords.addAll(records)
    }
}
