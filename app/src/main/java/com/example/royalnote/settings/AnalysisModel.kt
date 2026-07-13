package com.example.royalnote.settings

enum class ReasoningEffort(val wireValue: String) {
    MAX("max"),
    XHIGH("xhigh"),
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low"),
    NONE("none");

    companion object {
        fun fromWireValue(value: String?): ReasoningEffort? = entries.firstOrNull {
            it.wireValue == value
        }
    }
}

enum class AnalysisModel(
    val storageValue: String,
    val displayName: String,
    val openRouterId: String,
    val supportedEfforts: List<ReasoningEffort>,
) {
    DEEPSEEK_V4_PRO(
        storageValue = "deepseek-v4-pro",
        displayName = "DeepSeek V4 Pro",
        openRouterId = "deepseek/deepseek-v4-pro",
        supportedEfforts = listOf(ReasoningEffort.XHIGH, ReasoningEffort.HIGH),
    ),
    GPT_LATEST(
        storageValue = "gpt-latest",
        displayName = "GPT Latest",
        openRouterId = "~openai/gpt-latest",
        supportedEfforts = listOf(
            ReasoningEffort.MAX,
            ReasoningEffort.XHIGH,
            ReasoningEffort.HIGH,
            ReasoningEffort.MEDIUM,
            ReasoningEffort.LOW,
            ReasoningEffort.NONE,
        ),
    ),
    GEMINI_PRO_LATEST(
        storageValue = "gemini-pro-latest",
        displayName = "Gemini Pro Latest",
        openRouterId = "~google/gemini-pro-latest",
        supportedEfforts = listOf(
            ReasoningEffort.HIGH,
            ReasoningEffort.MEDIUM,
            ReasoningEffort.LOW,
        ),
    );

    companion object {
        fun fromStorageValue(value: String?): AnalysisModel? = entries.firstOrNull {
            it.storageValue == value
        }
    }
}
