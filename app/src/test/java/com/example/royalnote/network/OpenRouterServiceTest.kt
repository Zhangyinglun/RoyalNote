package com.example.royalnote.network

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class OpenRouterServiceTest {
    @Test
    fun systemPromptIncludesCurrentLocalDateTimeAndZone() {
        val clock = Clock.fixed(
            Instant.parse("2026-06-21T02:00:00Z"),
            ZoneId.of("Asia/Shanghai"),
        )

        val prompt = buildSystemPrompt(clock)

        assertTrue(prompt.contains("当前本地日期时间：2026-06-21T10:00:00"))
        assertTrue(prompt.contains("当前时区：Asia/Shanghai"))
    }
}
