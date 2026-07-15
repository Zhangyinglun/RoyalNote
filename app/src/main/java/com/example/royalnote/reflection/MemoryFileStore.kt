package com.example.royalnote.reflection

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface LongTermMemoryStore {
    val entries: StateFlow<List<MemoryEntry>>
    suspend fun load(): List<MemoryEntry>
    suspend fun markdown(): String
    suspend fun add(category: MemoryCategory, content: String): MemoryEntry?
    suspend fun update(id: String, content: String): Boolean
    suspend fun delete(id: String): Boolean
    suspend fun terminate(id: String): Boolean
}

class MemoryFileStore(
    private val file: File,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LongTermMemoryStore {
    private val mutex = Mutex()
    private val mutableEntries = MutableStateFlow<List<MemoryEntry>>(emptyList())
    override val entries: StateFlow<List<MemoryEntry>> = mutableEntries.asStateFlow()

    override suspend fun load(): List<MemoryEntry> = withContext(dispatcher) {
        mutex.withLock {
            val parsed = if (file.exists()) parseMemory(file.readText()) else emptyList()
            mutableEntries.value = parsed
            parsed
        }
    }

    override suspend fun markdown(): String = withContext(dispatcher) {
        mutex.withLock {
            ensureLoadedLocked()
            renderMemory(mutableEntries.value)
        }
    }

    override suspend fun add(category: MemoryCategory, content: String): MemoryEntry? =
        mutate { current ->
            val normalized = normalizeContent(content) ?: return@mutate null
            current.firstOrNull {
                it.category == category && it.content.equals(normalized, ignoreCase = true)
            }?.let { existing -> return@mutate Mutation(current, existing) }
            val nextNumber = current.asSequence()
                .filter { it.category == category }
                .mapNotNull { it.id.substringAfter('-', "").toIntOrNull() }
                .maxOrNull()
                ?.plus(1)
                ?: 1
            val entry = MemoryEntry(
                id = "%s-%03d".format(Locale.US, category.idPrefix, nextNumber),
                category = category,
                status = category.defaultStatus,
                date = LocalDate.now(clock).toString(),
                content = normalized,
            )
            val updated = current + entry
            val compacted = if (category == MemoryCategory.PROGRESS) {
                val recentProgress = updated.filter { it.category == MemoryCategory.PROGRESS }.takeLast(5)
                updated.filterNot { it.category == MemoryCategory.PROGRESS } + recentProgress
            } else {
                updated
            }
            Mutation(compacted, entry)
        }

    override suspend fun update(id: String, content: String): Boolean = mutate { current ->
        val normalized = normalizeContent(content) ?: return@mutate null
        if (current.none { it.id == id }) return@mutate Mutation(current, false)
        Mutation(current.map { if (it.id == id) it.copy(content = normalized) else it }, true)
    } ?: false

    override suspend fun delete(id: String): Boolean = mutate { current ->
        if (current.none { it.id == id }) return@mutate Mutation(current, false)
        Mutation(current.filterNot { it.id == id }, true)
    } ?: false

    override suspend fun terminate(id: String): Boolean = mutate { current ->
        val target = current.firstOrNull { it.id == id } ?: return@mutate Mutation(current, false)
        if (target.category !in setOf(MemoryCategory.ACTION, MemoryCategory.GOAL)) {
            return@mutate Mutation(current, false)
        }
        Mutation(current.map {
            if (it.id == id) it.copy(status = "已终止") else it
        }, true)
    } ?: false

    private suspend fun <T> mutate(block: (List<MemoryEntry>) -> Mutation<T>?): T? =
        withContext(dispatcher) {
            mutex.withLock {
                ensureLoadedLocked()
                val mutation = block(mutableEntries.value) ?: return@withLock null
                val markdown = renderMemory(mutation.entries)
                if (markdown.length > MAX_MEMORY_CHARACTERS) return@withLock null
                writeAtomically(markdown)
                mutableEntries.value = mutation.entries
                mutation.result
            }
        }

    private fun ensureLoadedLocked() {
        if (mutableEntries.value.isEmpty() && file.exists()) {
            mutableEntries.value = parseMemory(file.readText())
        }
    }

    private fun writeAtomically(content: String) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(content)
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private data class Mutation<T>(val entries: List<MemoryEntry>, val result: T)

    companion object {
        const val MAX_MEMORY_CHARACTERS = 4_000
    }
}

private val memoryLinePattern = Regex(
    "^- \\[([A-Z]-\\d{3})]\\[([^]]+)]\\[(\\d{4}-\\d{2}-\\d{2})] (.+)$"
)

internal fun parseMemory(markdown: String): List<MemoryEntry> {
    var category: MemoryCategory? = null
    return buildList {
        markdown.lineSequence().forEach { line ->
            if (line.startsWith("## ")) {
                val heading = line.removePrefix("## ").trim()
                category = MemoryCategory.entries.firstOrNull { it.heading == heading }
            } else {
                val match = memoryLinePattern.matchEntire(line.trim()) ?: return@forEach
                val currentCategory = category ?: return@forEach
                val (id, status, date, content) = match.destructured
                if (id.substringBefore('-') != currentCategory.idPrefix) return@forEach
                add(MemoryEntry(id, currentCategory, status, date, content))
            }
        }
    }
}

internal fun renderMemory(entries: List<MemoryEntry>): String = buildString {
    appendLine("# 起居注长期记忆")
    appendLine()
    appendLine("## 规则")
    appendLine()
    appendLine("- 只保存用户明确表达、接受或确认，并且对未来反思仍有帮助的信息。")
    appendLine("- 不保存完整日记、每日回顾、聊天原文、诊断、人格标签或危机等级。")
    appendLine("- 每条内容使用稳定 ID；更新时保留原 ID。")
    MemoryCategory.entries.forEach { category ->
        appendLine()
        appendLine("## ${category.heading}")
        appendLine()
        entries.filter { it.category == category }.forEach { entry ->
            appendLine("- [${entry.id}][${entry.status}][${entry.date}] ${entry.content}")
        }
    }
}.trimEnd() + "\n"

private fun normalizeContent(content: String): String? = content
    .replace(Regex("[\\r\\n]+"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
    .takeIf { it.length in 2..500 }
