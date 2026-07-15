package com.example.royalnote.reflection

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MemoryFileStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val clock = Clock.fixed(Instant.parse("2026-07-13T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun entriesRoundTripThroughMarkdownWithStableIds() = runTest {
        val file = temporaryFolder.newFile("MEMORY.md")
        val store = MemoryFileStore(file, clock)
        store.load()

        val goal = assertNotNull(store.add(MemoryCategory.GOAL, "减少晚间工作的影响"))
            .let { store.entries.value.single() }
        val action = assertNotNull(store.add(MemoryCategory.ACTION, "两个晚上在十点半结束工作"))
            .let { store.entries.value.last() }

        assertEquals("G-001", goal.id)
        assertEquals("A-001", action.id)
        assertTrue(store.update(goal.id, "让晚间更容易放松"))
        assertTrue(store.terminate(action.id))

        val reloaded = MemoryFileStore(file, clock)
        val entries = reloaded.load()
        assertEquals("让晚间更容易放松", entries.first { it.id == goal.id }.content)
        assertEquals("已终止", entries.first { it.id == action.id }.status)
        assertTrue(reloaded.markdown().contains("# 起居注长期记忆"))

        assertTrue(reloaded.delete(goal.id))
        assertFalse(reloaded.entries.value.any { it.id == goal.id })
    }
}
