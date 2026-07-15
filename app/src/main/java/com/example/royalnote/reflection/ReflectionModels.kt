package com.example.royalnote.reflection

import kotlinx.serialization.Serializable

@Serializable
data class ReflectionRecordSnapshot(
    val id: Long,
    val eventText: String,
    val moodTag: String? = null,
    val moodNote: String? = null,
    val startedAt: Long,
    val endedAt: Long,
    val eventDate: String = "",
)

@Serializable
data class ReflectionPeriod(
    val startDate: String,
    val endDate: String,
)

@Serializable
data class ReflectionCoverage(
    val recordCount: Int = 0,
    val recordedDates: List<String> = emptyList(),
)

@Serializable
data class ReflectionEvidenceItem(
    val text: String = "",
    val evidenceRecordIds: List<Long> = emptyList(),
)

@Serializable
data class ReflectionBlindSpot(
    val hypothesis: String = "",
    val alternativeExplanation: String = "",
    val uncertainty: String = "",
    val question: String = "",
    val evidenceRecordIds: List<Long> = emptyList(),
)

@Serializable
data class ReflectionExperiment(
    val id: String = "",
    val title: String = "",
    val action: String = "",
    val frequency: String = "",
    val observation: String = "",
)

@Serializable
data class SevenDayReflection(
    val period: ReflectionPeriod,
    val coverage: ReflectionCoverage = ReflectionCoverage(),
    val summary: List<ReflectionEvidenceItem> = emptyList(),
    val affirmations: List<ReflectionEvidenceItem> = emptyList(),
    val pressureSignals: List<ReflectionEvidenceItem> = emptyList(),
    val recoverySignals: List<ReflectionEvidenceItem> = emptyList(),
    val blindSpots: List<ReflectionBlindSpot> = emptyList(),
    val reflectionQuestion: String = "",
    val experiments: List<ReflectionExperiment> = emptyList(),
)

enum class ReflectionMessageRole(val wireValue: String) {
    USER("user"),
    ASSISTANT("assistant");

    companion object {
        fun fromWireValue(value: String): ReflectionMessageRole = entries.firstOrNull {
            it.wireValue == value
        } ?: ASSISTANT
    }
}

enum class MessageDeliveryState {
    SENDING,
    SENT,
    FAILED,
}

enum class CandidateStatus {
    PENDING,
    AUTO_SAVED,
    ACCEPTED,
    REJECTED,
}

enum class CandidatePolicy {
    AUTO,
    CONFIRM,
}

enum class MemoryCategory(
    val wireValue: String,
    val heading: String,
    val idPrefix: String,
    val defaultStatus: String,
) {
    PREFERENCE("preference", "偏好", "P", "有效"),
    FOCUS("focus", "关注", "F", "有效"),
    GOAL("goal", "目标", "G", "进行中"),
    ACTION("action", "行动", "A", "进行中"),
    INSIGHT("insight", "已确认认识", "I", "有效"),
    PROGRESS("progress", "最近进展", "R", "记录");

    companion object {
        fun fromWireValue(value: String): MemoryCategory? = entries.firstOrNull {
            it.wireValue == value.lowercase()
        }
    }
}

data class MemoryEntry(
    val id: String,
    val category: MemoryCategory,
    val status: String,
    val date: String,
    val content: String,
)

enum class ExperimentStatus {
    ACCEPTED,
    SKIPPED,
    TERMINATED,
}

data class ReflectionHistoryItem(
    val threadDate: String,
    val periodStartDate: String,
    val periodEndDate: String,
    val messageCount: Int,
)

data class ReflectionGenerationInput(
    val period: ReflectionPeriod,
    val records: List<ReflectionRecordSnapshot>,
    val memoryMarkdown: String,
)

data class ReflectionConversationMessage(
    val id: Long,
    val role: ReflectionMessageRole,
    val content: String,
)

data class ReflectionChatInput(
    val threadDate: String,
    val reflection: SevenDayReflection,
    val records: List<ReflectionRecordSnapshot>,
    val memoryMarkdown: String,
    val conversationSummary: String,
    val recentMessages: List<ReflectionConversationMessage>,
)

@Serializable
data class ChatMemoryCandidate(
    val category: String = "",
    val content: String = "",
    val sourceQuote: String = "",
    val explicit: Boolean = false,
)

@Serializable
data class ReflectionChatResult(
    val reply: String = "",
    val safetyMode: String = "normal",
    val memoryCandidates: List<ChatMemoryCandidate> = emptyList(),
)

interface ReflectionAiGateway {
    suspend fun generateReflection(input: ReflectionGenerationInput): SevenDayReflection
    suspend fun chat(input: ReflectionChatInput): ReflectionChatResult
    suspend fun compactConversation(
        existingSummary: String,
        messages: List<ReflectionConversationMessage>,
    ): String
}
