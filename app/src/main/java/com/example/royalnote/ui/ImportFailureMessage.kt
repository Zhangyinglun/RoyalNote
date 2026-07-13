package com.example.royalnote.ui

import com.example.royalnote.network.MissingOpenRouterApiKeyException
import com.example.royalnote.network.OpenRouterResponseException
import java.io.IOException
import kotlinx.coroutines.CancellationException

internal fun importFailureMessage(error: Throwable): String? = when (error) {
    is CancellationException -> null
    is MissingOpenRouterApiKeyException -> "请先在设置中填写 OpenRouter API Key"
    is OpenRouterResponseException -> "解析未成，稍后再试"
    is IOException -> "网络不通，稍后再试"
    else -> "解析未成，稍后再试"
}
