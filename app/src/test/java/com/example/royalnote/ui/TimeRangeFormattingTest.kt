package com.example.royalnote.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TimeRangeFormattingTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun convertsLocalDateToUtcDatePickerMillis() {
        val millis = Instant.parse("2026-07-11T16:15:00Z").toEpochMilli()

        val result = toDatePickerMillis(millis, zone)

        assertEquals(Instant.parse("2026-07-12T00:00:00Z").toEpochMilli(), result)
    }

    @Test
    fun replacingDatePreservesLocalTime() {
        val millis = Instant.parse("2026-07-11T13:30:45.123Z").toEpochMilli()
        val selectedUtcDateMillis = Instant.parse("2026-07-15T00:00:00Z").toEpochMilli()

        val result = replaceDate(millis, selectedUtcDateMillis, zone)

        assertEquals(Instant.parse("2026-07-15T13:30:45.123Z").toEpochMilli(), result)
    }

    @Test
    fun replacingTimeClearsSecondsAndNanos() {
        val millis = Instant.parse("2026-07-11T13:30:45.987Z").toEpochMilli()

        val result = replaceTime(millis, hour = 8, minute = 5, zone)

        assertEquals(Instant.parse("2026-07-11T00:05:00Z").toEpochMilli(), result)
    }

    @Test
    fun replacingDateNormalizesDstGapForward() {
        val berlin = ZoneId.of("Europe/Berlin")
        val millis = Instant.parse("2026-03-28T01:30:45.123Z").toEpochMilli()
        val selectedUtcDateMillis = Instant.parse("2026-03-29T00:00:00Z").toEpochMilli()

        val result = replaceDate(millis, selectedUtcDateMillis, berlin)

        assertEquals(Instant.parse("2026-03-29T01:30:45.123Z").toEpochMilli(), result)
    }

    @Test
    fun replacingTimeNormalizesDstGapForward() {
        val berlin = ZoneId.of("Europe/Berlin")
        val millis = Instant.parse("2026-03-28T23:15:45.987Z").toEpochMilli()

        val result = replaceTime(millis, hour = 2, minute = 30, berlin)

        assertEquals(Instant.parse("2026-03-29T01:30:00Z").toEpochMilli(), result)
    }

    @Test
    fun replacingDateUsesEarlierOffsetDuringDstOverlap() {
        val berlin = ZoneId.of("Europe/Berlin")
        val millis = Instant.parse("2026-10-24T00:30:45.123Z").toEpochMilli()
        val selectedUtcDateMillis = Instant.parse("2026-10-25T00:00:00Z").toEpochMilli()

        val result = replaceDate(millis, selectedUtcDateMillis, berlin)

        assertEquals(Instant.parse("2026-10-25T00:30:45.123Z").toEpochMilli(), result)
    }

    @Test
    fun replacingTimeUsesEarlierOffsetDuringDstOverlap() {
        val berlin = ZoneId.of("Europe/Berlin")
        val millis = Instant.parse("2026-10-25T02:15:45.987Z").toEpochMilli()

        val result = replaceTime(millis, hour = 2, minute = 30, berlin)

        assertEquals(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(), result)
    }

    @Test
    fun formatsEditorDateAndTimeInLocalZone() {
        val millis = Instant.parse("2026-07-11T13:30:00Z").toEpochMilli()

        assertEquals("2026-07-11", formatEditorDate(millis, zone))
        assertEquals("21:30", formatEditorTime(millis, zone))
    }

    @Test
    fun formatsSameDayAndCrossDayRanges() {
        val start = Instant.parse("2026-07-11T13:30:00Z").toEpochMilli()
        val sameMinuteEnd = Instant.parse("2026-07-11T13:30:59Z").toEpochMilli()
        val sameDayEnd = Instant.parse("2026-07-11T15:00:00Z").toEpochMilli()
        val crossDayEnd = Instant.parse("2026-07-11T16:15:00Z").toEpochMilli()

        assertEquals("21:30\n｜\n23:00", formatRecordTimeRange(start, sameDayEnd, zone))
        assertEquals("07-11\n21:30\n｜\n07-12\n00:15", formatRecordTimeRange(start, crossDayEnd, zone))
        assertEquals("21:30", formatRecordTimeRange(start, start, zone))
        assertEquals("21:30", formatRecordTimeRange(start, sameMinuteEnd, zone))
    }
}
