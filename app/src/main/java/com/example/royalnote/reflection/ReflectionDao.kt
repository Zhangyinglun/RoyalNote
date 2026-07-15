package com.example.royalnote.reflection

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReflectionDao {
    @Query("SELECT * FROM daily_reflections WHERE threadDate = :threadDate LIMIT 1")
    suspend fun reflectionFor(threadDate: String): DailyReflectionEntity?

    @Query("SELECT * FROM daily_reflections WHERE threadDate < :threadDate ORDER BY threadDate DESC LIMIT 1")
    suspend fun latestReflectionBefore(threadDate: String): DailyReflectionEntity?

    @Upsert
    suspend fun upsertReflection(reflection: DailyReflectionEntity)

    @Upsert
    suspend fun upsertThread(thread: ReflectionThreadEntity)

    @Query("SELECT * FROM reflection_threads WHERE threadDate = :threadDate LIMIT 1")
    suspend fun threadFor(threadDate: String): ReflectionThreadEntity?

    @Query(
        """
        SELECT r.threadDate, r.periodStartDate, r.periodEndDate, COUNT(m.id) AS messageCount
        FROM daily_reflections r
        LEFT JOIN reflection_messages m ON m.threadDate = r.threadDate
        GROUP BY r.threadDate, r.periodStartDate, r.periodEndDate
        ORDER BY r.threadDate DESC
        """
    )
    fun observeHistory(): Flow<List<ReflectionHistoryRow>>

    @Query("SELECT * FROM reflection_messages WHERE threadDate = :threadDate ORDER BY createdAt ASC, id ASC")
    fun observeMessages(threadDate: String): Flow<List<ReflectionMessageEntity>>

    @Query("SELECT * FROM reflection_messages WHERE threadDate = :threadDate ORDER BY createdAt ASC, id ASC")
    suspend fun messagesFor(threadDate: String): List<ReflectionMessageEntity>

    @Insert
    suspend fun insertMessage(message: ReflectionMessageEntity): Long

    @Update
    suspend fun updateMessage(message: ReflectionMessageEntity)

    @Query("UPDATE reflection_messages SET deliveryState = :state WHERE id = :messageId")
    suspend fun updateMessageState(messageId: Long, state: String)

    @Query(
        """
        UPDATE reflection_messages
        SET deliveryState = 'FAILED'
        WHERE threadDate = :threadDate AND role = 'user' AND deliveryState = 'SENDING'
        """
    )
    suspend fun markInterruptedMessagesFailed(threadDate: String)

    @Query("SELECT * FROM memory_candidates WHERE threadDate = :threadDate ORDER BY createdAt ASC, id ASC")
    fun observeCandidates(threadDate: String): Flow<List<MemoryCandidateEntity>>

    @Query("SELECT * FROM memory_candidates WHERE status = 'PENDING' ORDER BY createdAt DESC, id DESC")
    fun observePendingCandidates(): Flow<List<MemoryCandidateEntity>>

    @Query("SELECT * FROM memory_candidates WHERE id = :candidateId LIMIT 1")
    suspend fun candidate(candidateId: Long): MemoryCandidateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandidates(candidates: List<MemoryCandidateEntity>)

    @Query("UPDATE memory_candidates SET status = :status, memoryEntryId = :memoryEntryId WHERE id = :candidateId")
    suspend fun updateCandidateStatus(candidateId: Long, status: String, memoryEntryId: String?)

    @Query("SELECT * FROM reflection_experiments WHERE threadDate = :threadDate")
    fun observeExperimentStates(threadDate: String): Flow<List<ReflectionExperimentStateEntity>>

    @Upsert
    suspend fun upsertExperimentState(state: ReflectionExperimentStateEntity)

    @Query(
        """
        UPDATE reflection_experiments
        SET status = 'TERMINATED', updatedAt = :updatedAt
        WHERE memoryEntryId = :memoryEntryId
        """
    )
    suspend fun terminateExperimentForMemory(memoryEntryId: String, updatedAt: Long)
}
