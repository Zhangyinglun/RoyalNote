package com.example.royalnote.reflection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPolicyTest {
    @Test
    fun explicitGoalWithExactSourceQuoteCanBeSavedAutomatically() {
        val message = "我想这周试着在十点半结束工作"
        val candidate = ChatMemoryCandidate(
            category = "goal",
            content = "本周尝试在 22:30 结束工作。",
            sourceQuote = "这周试着在十点半结束工作",
            explicit = true,
        )

        assertEquals(MemoryDecision.AUTO, MemoryPolicy.decide(candidate, message, "normal"))
    }

    @Test
    fun insightRequiresConfirmationAndDiagnosisIsRejected() {
        val insight = ChatMemoryCandidate(
            category = "insight",
            content = "忙碌时容易忽略休息。",
            sourceQuote = "忙碌时容易忽略休息",
            explicit = true,
        )
        val diagnosis = ChatMemoryCandidate(
            category = "insight",
            content = "用户患有焦虑症。",
            sourceQuote = "我最近有些担心",
            explicit = true,
        )

        assertEquals(MemoryDecision.CONFIRM, MemoryPolicy.decide(insight, insight.sourceQuote, "normal"))
        assertEquals(MemoryDecision.REJECT, MemoryPolicy.decide(diagnosis, diagnosis.sourceQuote, "normal"))
        assertEquals(MemoryDecision.REJECT, MemoryPolicy.decide(insight, insight.sourceQuote, "immediate_danger"))
    }

    @Test
    fun localCrisisDetectorOnlyMatchesExplicitFirstPersonDanger() {
        assertTrue(CrisisDetector.isExplicitImmediateDanger("我现在想结束生命"))
        assertFalse(CrisisDetector.isExplicitImmediateDanger("朋友说他曾经想结束生命，我很担心"))
    }
}
