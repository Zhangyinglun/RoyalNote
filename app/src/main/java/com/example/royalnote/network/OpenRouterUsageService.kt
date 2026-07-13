package com.example.royalnote.network

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

data class MonthlyUsage(val amountUsd: Double)

fun interface OpenRouterUsageProvider {
    suspend fun monthlyUsage(apiKey: String): MonthlyUsage
}

class InvalidOpenRouterApiKeyException : IOException("OpenRouter API key is invalid")

class OpenRouterUsageService(
    private val client: Call.Factory = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val endpoint: String = OpenRouterConfig.CURRENT_KEY_URL,
) : OpenRouterUsageProvider {
    override suspend fun monthlyUsage(apiKey: String): MonthlyUsage = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401) throw InvalidOpenRouterApiKeyException()
            if (!response.isSuccessful) throw IOException("Usage API error: ${response.code}")
            val decoded = json.decodeFromString(CurrentKeyResponse.serializer(), response.body.string())
            MonthlyUsage(decoded.data.usageMonthly)
        }
    }
}

@Serializable
private data class CurrentKeyResponse(val data: CurrentKeyData)

@Serializable
private data class CurrentKeyData(
    @SerialName("usage_monthly") val usageMonthly: Double,
)
