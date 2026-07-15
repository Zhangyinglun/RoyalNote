package com.example.royalnote.reflection

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReflectionContextTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val clock = Clock.fixed(Instant.parse("2026-07-13T17:00:00Z"), zone)

    @Test
    fun windowCoversSevenCompleteDaysEndingYesterday() {
        val window = ReflectionContextBuilder.window(clock)

        assertEquals("2026-07-13", window.threadDate.toString())
        assertEquals("2026-07-06", window.startDate.toString())
        assertEquals("2026-07-12", window.endDate.toString())
    }

    @Test
    fun validationDropsUnknownEvidenceAndRestrictsSparseData() {
        val input = ReflectionGenerationInput(
            period = ReflectionPeriod("2026-07-06", "2026-07-12"),
            records = listOf(
                snapshot(1, "散步"),
                snapshot(2, "读书"),
            ),
            memoryMarkdown = "",
        )
        val raw = SevenDayReflection(
            period = ReflectionPeriod("wrong", "wrong"),
            summary = listOf(
                ReflectionEvidenceItem("两次记录", listOf(1, 2)),
                ReflectionEvidenceItem("不存在的记录", listOf(99)),
            ),
            blindSpots = listOf(
                ReflectionBlindSpot(
                    hypothesis = "形成模式",
                    question = "是真的吗？",
                    evidenceRecordIds = listOf(1),
                )
            ),
            reflectionQuestion = "  哪一刻值得回望？  ",
            experiments = listOf(
                ReflectionExperiment(title = "散步", action = "走十分钟")
            ),
        )

        val validated = validateReflection(raw, input, zone)

        assertEquals(input.period, validated.period)
        assertEquals(2, validated.coverage.recordCount)
        assertEquals(listOf("两次记录"), validated.summary.map { it.text })
        assertTrue(validated.blindSpots.isEmpty())
        assertTrue(validated.experiments.isEmpty())
        assertEquals("哪一刻值得回望？", validated.reflectionQuestion)
    }

    @Test
    fun validationKeepsOneSummaryAndAtMostOneBlindSpot() {
        val input = ReflectionGenerationInput(
            period = ReflectionPeriod("2026-07-06", "2026-07-12"),
            records = listOf(
                snapshot(1, "工作"),
                snapshot(2, "散步"),
                snapshot(3, "阅读"),
            ),
            memoryMarkdown = "",
        )
        val raw = SevenDayReflection(
            period = input.period,
            summary = listOf(
                ReflectionEvidenceItem("第一段总结", listOf(1, 2)),
                ReflectionEvidenceItem("不应保留的第二段", listOf(3)),
            ),
            affirmations = listOf(ReflectionEvidenceItem("不单独展示", listOf(2))),
            blindSpots = listOf(
                ReflectionBlindSpot("第一个盲区", question = "第一个问题", evidenceRecordIds = listOf(1)),
                ReflectionBlindSpot("第二个盲区", question = "第二个问题", evidenceRecordIds = listOf(2)),
            ),
        )

        val validated = validateReflection(raw, input, zone)

        assertEquals(listOf("第一段总结"), validated.summary.map { it.text })
        assertEquals(listOf("第一个盲区"), validated.blindSpots.map { it.hypothesis })
        assertTrue(validated.affirmations.isEmpty())
        assertTrue(validated.pressureSignals.isEmpty())
        assertTrue(validated.recoverySignals.isEmpty())
    }

    @Test
    fun recordedDatesPreferTheStoredEventDateOverTheQueryingDeviceZone() {
        val records = listOf(
            ReflectionRecordSnapshot(
                id = 1,
                eventText = "跨时区记录",
                startedAt = Instant.parse("2026-07-13T06:30:00Z").toEpochMilli(),
                endedAt = Instant.parse("2026-07-13T07:00:00Z").toEpochMilli(),
                eventDate = "2026-07-12",
            )
        )

        assertEquals(listOf("2026-07-12"), ReflectionContextBuilder.recordedDates(records, ZoneId.of("Asia/Shanghai")))
    }

    private fun snapshot(id: Long, event: String) = ReflectionRecordSnapshot(
        id = id,
        eventText = event,
        startedAt = Instant.parse("2026-07-10T17:00:00Z").toEpochMilli(),
        endedAt = Instant.parse("2026-07-10T17:30:00Z").toEpochMilli(),
    )
}
