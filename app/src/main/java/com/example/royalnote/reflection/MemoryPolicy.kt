package com.example.royalnote.reflection

enum class MemoryDecision {
    AUTO,
    CONFIRM,
    REJECT,
}

object MemoryPolicy {
    private val forbiddenTerms = listOf(
        "抑郁症",
        "焦虑症",
        "双相情感障碍",
        "人格障碍",
        "创伤依恋",
        "危险等级",
        "心理疾病",
    )

    fun decide(
        candidate: ChatMemoryCandidate,
        latestUserMessage: String,
        safetyMode: String,
    ): MemoryDecision {
        if (safetyMode != "normal") return MemoryDecision.REJECT
        val category = MemoryCategory.fromWireValue(candidate.category)
            ?: return MemoryDecision.REJECT
        val content = candidate.content.trim()
        if (content.length !in 2..500) return MemoryDecision.REJECT
        if (forbiddenTerms.any { content.contains(it, ignoreCase = true) }) {
            return MemoryDecision.REJECT
        }
        return if (
            category in setOf(MemoryCategory.GOAL, MemoryCategory.ACTION, MemoryCategory.PROGRESS) &&
            candidate.explicit &&
            candidate.sourceQuote.trim().length >= 2 &&
            latestUserMessage.contains(candidate.sourceQuote.trim())
        ) {
            MemoryDecision.AUTO
        } else {
            MemoryDecision.CONFIRM
        }
    }
}

object CrisisDetector {
    private val explicitPatterns = listOf(
        Regex("我.{0,5}(想|要|准备|打算).{0,4}(自杀|结束生命|伤害自己|杀死自己)"),
        Regex("我.{0,5}(想|要|准备|打算).{0,4}(杀人|伤害别人|伤害他人)"),
        Regex("我现在.{0,8}(有危险|控制不住|会伤害)"),
    )

    fun isExplicitImmediateDanger(text: String): Boolean = explicitPatterns.any { it.containsMatchIn(text) }
}

const val GENERIC_SAFETY_MESSAGE =
    "我会认真对待你刚才说的内容。请先确认：你现在是否正处于会伤害自己或他人的即时危险中？如果是，请立即联系当地紧急服务，或让一位能到场的可信任的人陪在你身边。这里的省察不能替代即时的现实支持。"
