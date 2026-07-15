package com.example.royalnote.reflection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationContextBudgetTest {
    @Test
    fun recentWindowKeepsNewestMessagesWithoutDeletingHistory() {
        val messages = (1L..8L).map { id ->
            ReflectionConversationMessage(
                id = id,
                role = if (id % 2L == 0L) ReflectionMessageRole.ASSISTANT else ReflectionMessageRole.USER,
                content = "x".repeat(40),
            )
        }

        val recent = ConversationContextBudget.recentMessages(messages, budget = 140)

        assertEquals(listOf(7L, 8L), recent.map { it.id })
        assertEquals(8, messages.size)
    }

    @Test
    fun compactionOnlyUsesMessagesAfterPreviousSummary() {
        val messages = (1L..5L).map { id ->
            ReflectionConversationMessage(id, ReflectionMessageRole.USER, "message-$id")
        }

        val chunk = ConversationContextBudget.compactionChunk(messages, summarizedThroughMessageId = 3)

        assertEquals(listOf(4L, 5L), chunk.map { it.id })
        assertTrue(chunk.all { it.id > 3 })
    }
}
