package com.example.royalnote.ui

import com.example.royalnote.data.NoteRecord
import com.example.royalnote.reflection.CandidateStatus
import com.example.royalnote.reflection.DailyReflectionEntity
import com.example.royalnote.reflection.GENERIC_SAFETY_MESSAGE
import com.example.royalnote.reflection.LongTermMemoryStore
import com.example.royalnote.reflection.MemoryCandidateEntity
import com.example.royalnote.reflection.MemoryCategory
import com.example.royalnote.reflection.MemoryEntry
import com.example.royalnote.reflection.MessageDeliveryState
import com.example.royalnote.reflection.ReflectionAiGateway
import com.example.royalnote.reflection.ReflectionChatInput
import com.example.royalnote.reflection.ReflectionChatResult
import com.example.royalnote.reflection.ReflectionConversationMessage
import com.example.royalnote.reflection.ReflectionEvidenceItem
import com.example.royalnote.reflection.ReflectionExperimentStateEntity
import com.example.royalnote.reflection.ReflectionGenerationInput
import com.example.royalnote.reflection.ReflectionHistoryItem
import com.example.royalnote.reflection.ReflectionMessageEntity
import com.example.royalnote.reflection.ReflectionMessageRole
import com.example.royalnote.reflection.ReflectionOperations
import com.example.royalnote.reflection.ReflectionThreadEntity
import com.example.royalnote.reflection.SevenDayReflection
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReflectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(
        Instant.parse("2026-07-13T17:00:00Z"),
        ZoneId.of("America/Los_Angeles"),
    )
    private lateinit var repository: FakeReflectionOperations
    private lateinit var gateway: FakeReflectionGateway

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeReflectionOperations()
        gateway = FakeReflectionGateway()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun firstVisibleWithNoRecordsCreatesLocalEmptyReflectionWithoutCallingModel() = runTest {
        val viewModel = ReflectionViewModel(repository, gateway, clock)

        viewModel.onScreenVisible()
        advanceUntilIdle()

        val stored = repository.reflections.getValue("2026-07-13")
        assertEquals("2026-07-06", stored.periodStartDate)
        assertEquals("2026-07-12", stored.periodEndDate)
        assertEquals(0, gateway.generationCalls)
        assertEquals(0, viewModel.uiState.value.reflection?.coverage?.recordCount)

        viewModel.onScreenVisible()
        advanceUntilIdle()
        assertEquals(0, gateway.generationCalls)
        assertEquals(1, repository.saveReflectionCalls)
    }

    @Test
    fun generatedReflectionRunsOnceAndUsesOnlyPreviousSevenCompleteDays() = runTest {
        repository.records = listOf(noteRecord(1, "散步"))
        gateway.generationResult = gateway.generationResult.copy(
            summary = listOf(ReflectionEvidenceItem("留下了一则散步记录", listOf(1)))
        )
        val viewModel = ReflectionViewModel(repository, gateway, clock)

        viewModel.onScreenVisible()
        advanceUntilIdle()
        viewModel.onScreenVisible()
        advanceUntilIdle()

        assertEquals(1, gateway.generationCalls)
        assertEquals("2026-07-06", gateway.lastGenerationInput?.period?.startDate)
        assertEquals("2026-07-12", gateway.lastGenerationInput?.period?.endDate)
        assertEquals("2026-07-06", repository.lastStartDate)
        assertEquals("2026-07-13", repository.lastEndDate)
        assertEquals("留下了一则散步记录", viewModel.uiState.value.reflection?.summary?.single()?.text)
    }

    @Test
    fun retryNeverOverwritesASuccessfulDailyReflection() = runTest {
        repository.records = listOf(noteRecord(1, "散步"))
        val viewModel = ReflectionViewModel(repository, gateway, clock)

        viewModel.onScreenVisible()
        advanceUntilIdle()
        viewModel.retryGeneration()
        advanceUntilIdle()

        assertEquals(1, gateway.generationCalls)
        assertEquals(1, repository.saveReflectionCalls)
    }

    @Test
    fun firstVisibleAfterMidnightCreatesTheNewDailyReflection() = runTest {
        val mutableClock = MutableClock(
            Instant.parse("2026-07-13T17:00:00Z"),
            ZoneId.of("America/Los_Angeles"),
        )
        repository.records = listOf(noteRecord(1, "散步"))
        val viewModel = ReflectionViewModel(repository, gateway, mutableClock)

        viewModel.onScreenVisible()
        advanceUntilIdle()
        mutableClock.instant = Instant.parse("2026-07-14T17:00:00Z")
        viewModel.onScreenVisible()
        advanceUntilIdle()

        assertEquals(2, gateway.generationCalls)
        assertEquals(setOf("2026-07-13", "2026-07-14"), repository.reflections.keys)
        assertEquals("2026-07-14", viewModel.uiState.value.todayThreadDate)
        assertEquals("2026-07-07", gateway.lastGenerationInput?.period?.startDate)
        assertEquals("2026-07-13", gateway.lastGenerationInput?.period?.endDate)
    }

    @Test
    fun explicitImmediateDangerGetsFixedLocalReplyAndNeverCallsChatModel() = runTest {
        val viewModel = ReflectionViewModel(repository, gateway, clock)
        viewModel.onScreenVisible()
        advanceUntilIdle()

        viewModel.updateInputText("我现在想结束生命")
        viewModel.sendMessage()
        advanceUntilIdle()

        val messages = repository.messages.getValue("2026-07-13").value
        assertEquals(2, messages.size)
        assertEquals(MessageDeliveryState.SENT.name, messages.first().deliveryState)
        assertEquals(ReflectionMessageRole.ASSISTANT.wireValue, messages.last().role)
        assertEquals(GENERIC_SAFETY_MESSAGE, messages.last().content)
        assertTrue(messages.last().isSafetyMessage)
        assertEquals(0, gateway.chatCalls)
        assertTrue(repository.candidates.getValue("2026-07-13").value.isEmpty())
    }

    private fun noteRecord(id: Long, text: String) = NoteRecord(
        id = id,
        eventText = text,
        moodTag = null,
        moodNote = null,
        startedAt = Instant.parse("2026-07-10T17:00:00Z").toEpochMilli(),
        endedAt = Instant.parse("2026-07-10T17:30:00Z").toEpochMilli(),
        eventDate = "2026-07-10",
        zoneId = "America/Los_Angeles",
        source = com.example.royalnote.data.RecordSources.MANUAL,
        createdAt = clock.millis(),
        updatedAt = clock.millis(),
    )
}

private class MutableClock(
    var instant: Instant,
    private val zoneId: ZoneId,
) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
    override fun instant(): Instant = instant
}

private class FakeReflectionGateway : ReflectionAiGateway {
    var generationCalls = 0
    var chatCalls = 0
    var lastGenerationInput: ReflectionGenerationInput? = null
    var generationResult = SevenDayReflection(
        period = com.example.royalnote.reflection.ReflectionPeriod("ignored", "ignored")
    )

    override suspend fun generateReflection(input: ReflectionGenerationInput): SevenDayReflection {
        generationCalls += 1
        lastGenerationInput = input
        return generationResult
    }

    override suspend fun chat(input: ReflectionChatInput): ReflectionChatResult {
        chatCalls += 1
        return ReflectionChatResult(reply = "回复")
    }

    override suspend fun compactConversation(
        existingSummary: String,
        messages: List<ReflectionConversationMessage>,
    ): String = "压缩摘要"
}

private class FakeReflectionOperations : ReflectionOperations {
    var records: List<NoteRecord> = emptyList()
    var lastStartDate: String? = null
    var lastEndDate: String? = null
    val reflections = mutableMapOf<String, DailyReflectionEntity>()
    val threads = mutableMapOf<String, ReflectionThreadEntity>()
    val messages = mutableMapOf<String, MutableStateFlow<List<ReflectionMessageEntity>>>()
    val candidates = mutableMapOf<String, MutableStateFlow<List<MemoryCandidateEntity>>>()
    val experiments = mutableMapOf<String, MutableStateFlow<List<ReflectionExperimentStateEntity>>>()
    private val history = MutableStateFlow<List<ReflectionHistoryItem>>(emptyList())
    private val pending = MutableStateFlow<List<MemoryCandidateEntity>>(emptyList())
    private val memories = MutableStateFlow<List<MemoryEntry>>(emptyList())
    override val memoryEntries: StateFlow<List<MemoryEntry>> = memories
    var saveReflectionCalls = 0
    private var nextMessageId = 1L
    private var nextCandidateId = 1L

    override suspend fun loadMemory(): List<MemoryEntry> = memories.value
    override suspend fun memoryMarkdown(): String = "# 起居注长期记忆\n"
    override suspend fun addMemory(category: MemoryCategory, content: String): MemoryEntry {
        val entry = MemoryEntry(
            id = "${category.idPrefix}-001",
            category = category,
            status = category.defaultStatus,
            date = "2026-07-13",
            content = content,
        )
        memories.value = memories.value + entry
        return entry
    }

    override suspend fun updateMemory(id: String, content: String): Boolean {
        memories.value = memories.value.map { if (it.id == id) it.copy(content = content) else it }
        return true
    }

    override suspend fun deleteMemory(id: String): Boolean {
        memories.value = memories.value.filterNot { it.id == id }
        return true
    }

    override suspend fun terminateMemory(id: String, nowMillis: Long): Boolean = true
    override suspend fun recordsInDateRange(
        startDateInclusive: String,
        endDateExclusive: String,
    ): List<NoteRecord> {
        lastStartDate = startDateInclusive
        lastEndDate = endDateExclusive
        return records
    }
    override suspend fun reflectionFor(threadDate: String): DailyReflectionEntity? = reflections[threadDate]
    override suspend fun latestReflectionBefore(threadDate: String): DailyReflectionEntity? = reflections.values
        .filter { it.threadDate < threadDate }
        .maxByOrNull { it.threadDate }

    override suspend fun saveReflection(
        reflection: DailyReflectionEntity,
        thread: ReflectionThreadEntity,
    ) {
        saveReflectionCalls += 1
        reflections[reflection.threadDate] = reflection
        threads[thread.threadDate] = thread
        history.value = reflections.values.sortedByDescending { it.threadDate }.map {
            ReflectionHistoryItem(it.threadDate, it.periodStartDate, it.periodEndDate, 0)
        }
    }

    override fun observeHistory(): Flow<List<ReflectionHistoryItem>> = history
    override fun observeMessages(threadDate: String): Flow<List<ReflectionMessageEntity>> =
        messages.getOrPut(threadDate) { MutableStateFlow(emptyList()) }

    override suspend fun messagesFor(threadDate: String): List<ReflectionMessageEntity> =
        messages.getOrPut(threadDate) { MutableStateFlow(emptyList()) }.value

    override suspend fun insertMessage(message: ReflectionMessageEntity): Long {
        val id = nextMessageId++
        val flow = messages.getOrPut(message.threadDate) { MutableStateFlow(emptyList()) }
        flow.value = flow.value + message.copy(id = id)
        return id
    }

    override suspend fun updateMessageState(messageId: Long, state: MessageDeliveryState) {
        messages.values.forEach { flow ->
            flow.value = flow.value.map { if (it.id == messageId) it.copy(deliveryState = state.name) else it }
        }
    }

    override suspend fun markInterruptedMessagesFailed(threadDate: String) {
        val flow = messages.getOrPut(threadDate) { MutableStateFlow(emptyList()) }
        flow.value = flow.value.map {
            if (it.deliveryState == MessageDeliveryState.SENDING.name) {
                it.copy(deliveryState = MessageDeliveryState.FAILED.name)
            } else it
        }
    }

    override suspend fun threadFor(threadDate: String): ReflectionThreadEntity? = threads[threadDate]
    override suspend fun saveThread(thread: ReflectionThreadEntity) {
        threads[thread.threadDate] = thread
    }

    override fun observeCandidates(threadDate: String): Flow<List<MemoryCandidateEntity>> =
        candidates.getOrPut(threadDate) { MutableStateFlow(emptyList()) }

    override fun observePendingCandidates(): Flow<List<MemoryCandidateEntity>> = pending
    override suspend fun candidate(candidateId: Long): MemoryCandidateEntity? = candidates.values
        .asSequence()
        .flatMap { it.value.asSequence() }
        .firstOrNull { it.id == candidateId }

    override suspend fun insertCandidates(candidates: List<MemoryCandidateEntity>) {
        candidates.groupBy { it.threadDate }.forEach { (threadDate, values) ->
            val flow = this.candidates.getOrPut(threadDate) { MutableStateFlow(emptyList()) }
            flow.value = flow.value + values.map { it.copy(id = nextCandidateId++) }
        }
        pending.value = this.candidates.values.flatMap { it.value }
            .filter { it.status == CandidateStatus.PENDING.name }
    }

    override suspend fun updateCandidateStatus(
        candidateId: Long,
        status: CandidateStatus,
        memoryEntryId: String?,
    ) {
        candidates.values.forEach { flow ->
            flow.value = flow.value.map {
                if (it.id == candidateId) it.copy(status = status.name, memoryEntryId = memoryEntryId) else it
            }
        }
        pending.value = candidates.values.flatMap { it.value }
            .filter { it.status == CandidateStatus.PENDING.name }
    }

    override fun observeExperimentStates(threadDate: String): Flow<List<ReflectionExperimentStateEntity>> =
        experiments.getOrPut(threadDate) { MutableStateFlow(emptyList()) }

    override suspend fun saveExperimentState(state: ReflectionExperimentStateEntity) {
        val flow = experiments.getOrPut(state.threadDate) { MutableStateFlow(emptyList()) }
        flow.value = flow.value.filterNot { it.experimentId == state.experimentId } + state
    }
}
