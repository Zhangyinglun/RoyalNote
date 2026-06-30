package com.example.royalnote.network

import kotlinx.serialization.Serializable

@Serializable
data class ParsedRecords(
    val records: List<ParsedRecord> = emptyList(),
)

@Serializable
data class ParsedRecord(
    val eventText: String,
    val moodTag: String? = null,
    val moodNote: String? = null,
    val timestamp: String,
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val response_format: ResponseFormat? = null,
    val reasoning: ReasoningConfig? = null,
    val max_tokens: Int? = null,
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ResponseFormat(
    val type: String,
)

@Serializable
data class ReasoningConfig(
    val effort: String,
    val exclude: Boolean = true,
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList(),
)

@Serializable
data class Choice(
    val message: ChatMessage,
)
