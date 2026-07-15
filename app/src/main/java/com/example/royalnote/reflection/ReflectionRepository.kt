package com.example.royalnote.reflection

import com.example.royalnote.data.NoteRecord
import com.example.royalnote.data.NoteRecordDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

interface ReflectionOperations {
    val memoryEntries: StateFlow<List<MemoryEntry>>
    suspend fun loadMemory(): List<MemoryEntry>
    suspend fun memoryMarkdown(): String
    suspend fun addMemory(category: MemoryCategory, content: String): MemoryEntry?
    suspend fun updateMemory(id: String, content: String): Boolean
    suspend fun deleteMemory(id: String): Boolean
    suspend fun terminateMemory(id: String, nowMillis: Long): Boolean

    suspend fun recordsInDateRange(
        startDateInclusive: String,
        endDateExclusive: String,
    ): List<NoteRecord>
    suspend fun reflectionFor(threadDate: String): DailyReflectionEntity?
    suspend fun latestReflectionBefore(threadDate: String): DailyReflectionEntity?
    suspend fun saveReflection(reflection: DailyReflectionEntity, thread: ReflectionThreadEntity)
    fun observeHistory(): Flow<List<ReflectionHistoryItem>>
    fun observeMessages(threadDate: String): Flow<List<ReflectionMessageEntity>>
    suspend fun messagesFor(threadDate: String): List<ReflectionMessageEntity>
    suspend fun insertMessage(message: ReflectionMessageEntity): Long
    suspend fun updateMessageState(messageId: Long, state: MessageDeliveryState)
    suspend fun markInterruptedMessagesFailed(threadDate: String)
    suspend fun threadFor(threadDate: String): ReflectionThreadEntity?
    suspend fun saveThread(thread: ReflectionThreadEntity)

    fun observeCandidates(threadDate: String): Flow<List<MemoryCandidateEntity>>
    fun observePendingCandidates(): Flow<List<MemoryCandidateEntity>>
    suspend fun candidate(candidateId: Long): MemoryCandidateEntity?
    suspend fun insertCandidates(candidates: List<MemoryCandidateEntity>)
    suspend fun updateCandidateStatus(
        candidateId: Long,
        status: CandidateStatus,
        memoryEntryId: String?,
    )

    fun observeExperimentStates(threadDate: String): Flow<List<ReflectionExperimentStateEntity>>
    suspend fun saveExperimentState(state: ReflectionExperimentStateEntity)
}

class ReflectionRepository(
    private val noteDao: NoteRecordDao,
    private val reflectionDao: ReflectionDao,
    private val memoryStore: LongTermMemoryStore,
) : ReflectionOperations {
    override val memoryEntries: StateFlow<List<MemoryEntry>> = memoryStore.entries

    override suspend fun loadMemory(): List<MemoryEntry> = memoryStore.load()
    override suspend fun memoryMarkdown(): String = memoryStore.markdown()
    override suspend fun addMemory(category: MemoryCategory, content: String): MemoryEntry? =
        memoryStore.add(category, content)

    override suspend fun updateMemory(id: String, content: String): Boolean =
        memoryStore.update(id, content)

    override suspend fun deleteMemory(id: String): Boolean = memoryStore.delete(id)

    override suspend fun terminateMemory(id: String, nowMillis: Long): Boolean {
        val terminated = memoryStore.terminate(id)
        if (terminated) reflectionDao.terminateExperimentForMemory(id, nowMillis)
        return terminated
    }

    override suspend fun recordsInDateRange(
        startDateInclusive: String,
        endDateExclusive: String,
    ): List<NoteRecord> = noteDao.recordsInDateRange(startDateInclusive, endDateExclusive)

    override suspend fun reflectionFor(threadDate: String): DailyReflectionEntity? =
        reflectionDao.reflectionFor(threadDate)

    override suspend fun latestReflectionBefore(threadDate: String): DailyReflectionEntity? =
        reflectionDao.latestReflectionBefore(threadDate)

    override suspend fun saveReflection(
        reflection: DailyReflectionEntity,
        thread: ReflectionThreadEntity,
    ) {
        reflectionDao.upsertReflection(reflection)
        reflectionDao.upsertThread(thread)
    }

    override fun observeHistory(): Flow<List<ReflectionHistoryItem>> =
        reflectionDao.observeHistory().map { rows ->
            rows.map { row ->
                ReflectionHistoryItem(
                    threadDate = row.threadDate,
                    periodStartDate = row.periodStartDate,
                    periodEndDate = row.periodEndDate,
                    messageCount = row.messageCount,
                )
            }
        }

    override fun observeMessages(threadDate: String): Flow<List<ReflectionMessageEntity>> =
        reflectionDao.observeMessages(threadDate)

    override suspend fun messagesFor(threadDate: String): List<ReflectionMessageEntity> =
        reflectionDao.messagesFor(threadDate)

    override suspend fun insertMessage(message: ReflectionMessageEntity): Long =
        reflectionDao.insertMessage(message)

    override suspend fun updateMessageState(messageId: Long, state: MessageDeliveryState) =
        reflectionDao.updateMessageState(messageId, state.name)

    override suspend fun markInterruptedMessagesFailed(threadDate: String) =
        reflectionDao.markInterruptedMessagesFailed(threadDate)

    override suspend fun threadFor(threadDate: String): ReflectionThreadEntity? =
        reflectionDao.threadFor(threadDate)

    override suspend fun saveThread(thread: ReflectionThreadEntity) =
        reflectionDao.upsertThread(thread)

    override fun observeCandidates(threadDate: String): Flow<List<MemoryCandidateEntity>> =
        reflectionDao.observeCandidates(threadDate)

    override fun observePendingCandidates(): Flow<List<MemoryCandidateEntity>> =
        reflectionDao.observePendingCandidates()

    override suspend fun candidate(candidateId: Long): MemoryCandidateEntity? =
        reflectionDao.candidate(candidateId)

    override suspend fun insertCandidates(candidates: List<MemoryCandidateEntity>) =
        reflectionDao.insertCandidates(candidates)

    override suspend fun updateCandidateStatus(
        candidateId: Long,
        status: CandidateStatus,
        memoryEntryId: String?,
    ) = reflectionDao.updateCandidateStatus(candidateId, status.name, memoryEntryId)

    override fun observeExperimentStates(threadDate: String): Flow<List<ReflectionExperimentStateEntity>> =
        reflectionDao.observeExperimentStates(threadDate)

    override suspend fun saveExperimentState(state: ReflectionExperimentStateEntity) =
        reflectionDao.upsertExperimentState(state)
}
