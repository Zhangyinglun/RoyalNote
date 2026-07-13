package com.example.royalnote.ui

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val editorDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val crossDayFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

private fun resolveLocalDateTime(localDateTime: LocalDateTime, zoneId: ZoneId): Long =
    localDateTime
        .atZone(zoneId)
        .withEarlierOffsetAtOverlap()
        .toInstant()
        .toEpochMilli()

internal fun toDatePickerMillis(millis: Long, zoneId: ZoneId): Long {
    val localDate = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
    return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

internal fun replaceDate(millis: Long, selectedUtcDateMillis: Long, zoneId: ZoneId): Long {
    val selectedDate = Instant.ofEpochMilli(selectedUtcDateMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
    val currentTime = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalTime()
    return resolveLocalDateTime(selectedDate.atTime(currentTime), zoneId)
}

internal fun replaceTime(millis: Long, hour: Int, minute: Int, zoneId: ZoneId): Long {
    val localDate = Instant.ofEpochMilli(millis)
        .atZone(zoneId)
        .toLocalDate()
    return resolveLocalDateTime(localDate.atTime(hour, minute), zoneId)
}

internal fun formatEditorDate(millis: Long, zoneId: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zoneId).format(editorDateFormatter)

internal fun formatEditorTime(millis: Long, zoneId: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zoneId).format(timeFormatter)

internal fun formatRecordTimeRange(startedAt: Long, endedAt: Long, zoneId: ZoneId): String {
    val start = Instant.ofEpochMilli(startedAt).atZone(zoneId)
    val end = Instant.ofEpochMilli(endedAt).atZone(zoneId)
    return if (start.toLocalDate() == end.toLocalDate()) {
        "${start.format(timeFormatter)}–${end.format(timeFormatter)}"
    } else {
        "${start.format(crossDayFormatter)}–${end.format(crossDayFormatter)}"
    }
}
