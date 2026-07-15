package com.example.royalnote.reflection

import com.example.royalnote.data.NoteRecord
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SevenDayWindow(
    val threadDate: LocalDate,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

object ReflectionContextBuilder {
    fun window(clock: Clock): SevenDayWindow {
        val today = LocalDate.now(clock)
        val endDate = today.minusDays(1)
        val startDate = endDate.minusDays(6)
        return SevenDayWindow(
            threadDate = today,
            startDate = startDate,
            endDate = endDate,
        )
    }

    fun snapshots(records: List<NoteRecord>): List<ReflectionRecordSnapshot> = records.map { record ->
        ReflectionRecordSnapshot(
            id = record.id,
            eventText = record.eventText,
            moodTag = record.moodTag,
            moodNote = record.moodNote,
            startedAt = record.startedAt,
            endedAt = record.endedAt,
            eventDate = record.eventDate,
        )
    }

    fun recordedDates(
        records: List<ReflectionRecordSnapshot>,
        zoneId: ZoneId,
    ): List<String> = records.asSequence()
        .map {
            it.eventDate.ifBlank {
                Instant.ofEpochMilli(it.startedAt).atZone(zoneId).toLocalDate().toString()
            }
        }
        .distinct()
        .sorted()
        .toList()
}

internal fun validateReflection(
    raw: SevenDayReflection,
    input: ReflectionGenerationInput,
    zoneId: ZoneId,
): SevenDayReflection {
    val validIds = input.records.mapTo(mutableSetOf()) { it.id }
    fun validItem(item: ReflectionEvidenceItem): Boolean =
        item.text.isNotBlank() &&
            item.evidenceRecordIds.isNotEmpty() &&
            item.evidenceRecordIds.all(validIds::contains)

    val limitedBlindSpots = if (input.records.size <= 2) {
        emptyList()
    } else {
        raw.blindSpots.asSequence()
            .filter { spot ->
                spot.hypothesis.isNotBlank() &&
                    spot.question.isNotBlank() &&
                    spot.evidenceRecordIds.isNotEmpty() &&
                    spot.evidenceRecordIds.all(validIds::contains)
            }
            .take(1)
            .map { it.trimmed() }
            .toList()
    }
    val experiments = if (input.records.size <= 2) emptyList() else raw.experiments
        .asSequence()
        .filter { it.title.isNotBlank() && it.action.isNotBlank() }
        .take(2)
        .mapIndexed { index, experiment ->
            experiment.copy(
                id = experiment.id.ifBlank { "experiment-${index + 1}" }.take(80),
                title = experiment.title.clean(120),
                action = experiment.action.clean(400),
                frequency = experiment.frequency.clean(160),
                observation = experiment.observation.clean(240),
            )
        }
        .toList()

    return SevenDayReflection(
        period = input.period,
        coverage = ReflectionCoverage(
            recordCount = input.records.size,
            recordedDates = ReflectionContextBuilder.recordedDates(input.records, zoneId),
        ),
        summary = raw.summary.filter(::validItem).take(1).map { it.trimmed() },
        affirmations = emptyList(),
        pressureSignals = emptyList(),
        recoverySignals = emptyList(),
        blindSpots = limitedBlindSpots,
        reflectionQuestion = raw.reflectionQuestion.clean(300),
        experiments = experiments,
    )
}

internal fun emptyReflection(period: ReflectionPeriod): SevenDayReflection = SevenDayReflection(
    period = period,
    coverage = ReflectionCoverage(),
    reflectionQuestion = "这段时间没有起居记录可参考。此刻你最想从哪里说起？",
)

private fun ReflectionEvidenceItem.trimmed() = copy(text = text.clean(800))

private fun ReflectionBlindSpot.trimmed() = copy(
    hypothesis = hypothesis.clean(500),
    alternativeExplanation = alternativeExplanation.clean(400),
    uncertainty = uncertainty.clean(240),
    question = question.clean(300),
)

private fun String.clean(maxLength: Int): String = replace(Regex("\\s+"), " ").trim().take(maxLength)

object ConversationContextBudget {
    const val RECENT_MESSAGES_CHARACTERS = 18_000
    const val COMPACTION_TRIGGER_CHARACTERS = 22_000
    const val COMPACTION_CHUNK_CHARACTERS = 18_000
    const val SNAPSHOT_CHARACTERS = 30_000
    const val REFLECTION_CHARACTERS = 14_000

    fun recentMessages(
        messages: List<ReflectionConversationMessage>,
        budget: Int = RECENT_MESSAGES_CHARACTERS,
    ): List<ReflectionConversationMessage> {
        var used = 0
        return messages.asReversed().takeWhile { message ->
            val cost = message.content.length + 24
            if (used + cost > budget && used > 0) false else {
                used += cost
                true
            }
        }.asReversed()
    }

    fun compactionChunk(
        messages: List<ReflectionConversationMessage>,
        summarizedThroughMessageId: Long,
    ): List<ReflectionConversationMessage> {
        var used = 0
        return messages.asSequence()
            .filter { it.id > summarizedThroughMessageId }
            .takeWhile { message ->
                val cost = message.content.length + 24
                if (used + cost > COMPACTION_CHUNK_CHARACTERS && used > 0) false else {
                    used += cost
                    true
                }
            }
            .toList()
    }

    fun shouldCompact(
        messages: List<ReflectionConversationMessage>,
        summarizedThroughMessageId: Long,
    ): Boolean = messages.asSequence()
        .filter { it.id > summarizedThroughMessageId }
        .sumOf { it.content.length + 24 } > COMPACTION_TRIGGER_CHARACTERS
}
