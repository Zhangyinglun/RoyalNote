package com.example.royalnote

import com.example.royalnote.ui.RecordOperations
import kotlinx.coroutines.flow.first
import java.time.ZoneId

object SeedData {
    suspend fun seedIfEmpty(ops: RecordOperations) {
        if (ops.observeRecords().first().isNotEmpty()) return

        val zone = ZoneId.systemDefault()
        val now = java.time.Instant.now().atZone(zone)
        val nowMillis = now.toInstant().toEpochMilli()
        ops.addRecord(
            eventText = "这里是起居注。录今日之事，存此刻之心。",
            moodTag = null,
            moodNote = null,
            startedAt = nowMillis,
            endedAt = nowMillis,
            nowMillis = nowMillis,
            zoneId = zone.id,
        )
    }
}
