package com.example.royalnote.network

object OpenRouterConfig {
    val API_KEY: String get() = com.example.royalnote.BuildConfig.OPENROUTER_API_KEY
    const val BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
    const val MODEL = "deepseek/deepseek-v4-flash"
    const val MAX_OUTPUT_TOKENS = 32_768
}
