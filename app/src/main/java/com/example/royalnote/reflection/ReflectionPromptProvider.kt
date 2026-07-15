package com.example.royalnote.reflection

import android.content.Context

interface ReflectionPromptProvider {
    val sevenDayReflection: String
    val reflectionChat: String
    val conversationCompaction: String
}

class AssetReflectionPromptProvider(context: Context) : ReflectionPromptProvider {
    private val assets = context.applicationContext.assets

    override val sevenDayReflection: String by lazy {
        assets.open("prompts/seven_day_reflection_v1.md").bufferedReader().use { it.readText() }
    }
    override val reflectionChat: String by lazy {
        assets.open("prompts/reflection_chat_v1.md").bufferedReader().use { it.readText() }
    }
    override val conversationCompaction: String by lazy {
        assets.open("prompts/memory_review_v1.md").bufferedReader().use { it.readText() }
    }
}

internal data class StaticReflectionPromptProvider(
    override val sevenDayReflection: String,
    override val reflectionChat: String,
    override val conversationCompaction: String,
) : ReflectionPromptProvider
