package com.example.royalnote.network

import com.example.royalnote.settings.OpenRouterRequestSettings
import com.example.royalnote.settings.OpenRouterSettingsProvider
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OpenRouterRequestIntegrationTest {
    @Test
    fun requestUsesOneTrimmedSettingsSnapshotAndNextRequestUsesUpdates() = runTest {
        var settings = OpenRouterRequestSettings(
            apiKey = "  first-key  ",
            modelId = "~openai/gpt-latest",
            effort = "xhigh",
        )
        val calls = ControllableCallFactory()
        val service = OpenRouterService(
            settingsProvider = OpenRouterSettingsProvider {
                settings.copy(apiKey = settings.apiKey.trim())
            },
            client = calls,
        )

        val first = async { service.parseRecords("昨天读书") }
        calls.awaitCallCount(1)
        val firstRequest = calls.calls.single().request()
        settings = OpenRouterRequestSettings(
            apiKey = "second-key",
            modelId = "~google/gemini-pro-latest",
            effort = "low",
        )

        assertEquals("Bearer first-key", firstRequest.header("Authorization"))
        assertRequestSettings(firstRequest, "~openai/gpt-latest", "xhigh")
        calls.calls.single().respondSuccess()
        first.await()

        val second = async { service.parseRecords("今天散步") }
        calls.awaitCallCount(2)
        val secondRequest = calls.calls[1].request()
        assertEquals("Bearer second-key", secondRequest.header("Authorization"))
        assertRequestSettings(secondRequest, "~google/gemini-pro-latest", "low")
        calls.calls[1].respondSuccess()
        second.await()
    }

    @Test
    fun cancellationCancelsHttpCallAndLateResponseCannotProduceResult() = runTest {
        val calls = ControllableCallFactory()
        val service = service(calls)
        var result: ParsedRecords? = null
        val job = launch { result = service.parseRecords("昨天读书") }
        calls.awaitCallCount(1)

        job.cancelAndJoin()

        val call = calls.calls.single()
        assertTrue(call.isCanceled())
        call.respondSuccess()
        runCurrent()
        assertNull(result)
        assertTrue(job.isCancelled)
    }

    @Test
    fun httpFailureIsAProtocolFailure() = runTest {
        listOf(400, 500).forEach { code ->
            val calls = ControllableCallFactory()
            val result = async { runCatching { service(calls).parseRecords("旧录") } }
            calls.awaitCallCount(1)
            calls.calls.single().respond(code, "{}")

            assertTrue(result.await().exceptionOrNull() is OpenRouterResponseException)
        }
    }

    @Test
    fun emptyAndMalformedResponsesAreProtocolFailures() = runTest {
        listOf(
            "",
            "{\"choices\":[]}",
            completionBody(""),
            completionBody("not-json"),
        ).forEach { body ->
            val calls = ControllableCallFactory()
            val result = async { runCatching { service(calls).parseRecords("旧录") } }
            calls.awaitCallCount(1)
            calls.calls.single().respond(200, body)

            assertTrue(result.await().exceptionOrNull() is OpenRouterResponseException)
        }
    }

    @Test
    fun transportFailureRemainsIOExceptionAndCancellationRemainsDistinct() = runTest {
        val transportCalls = ControllableCallFactory()
        val transport = async { runCatching { service(transportCalls).parseRecords("旧录") } }
        transportCalls.awaitCallCount(1)
        transportCalls.calls.single().fail(IOException("offline"))
        val transportError = transport.await().exceptionOrNull()
        assertTrue(transportError is IOException)
        assertFalse(transportError is OpenRouterResponseException)

        val cancellationCalls = ControllableCallFactory()
        val cancellation = async { service(cancellationCalls).parseRecords("旧录") }
        cancellationCalls.awaitCallCount(1)
        cancellation.cancel()
        val cancellationError = runCatching { cancellation.await() }.exceptionOrNull()
        assertTrue(cancellationError is CancellationException)
    }

    private fun service(calls: Call.Factory) = OpenRouterService(
        settingsProvider = OpenRouterSettingsProvider {
            OpenRouterRequestSettings("key", "deepseek/deepseek-v4-pro", "high")
        },
        client = calls,
    )

    private fun assertRequestSettings(request: Request, model: String, effort: String) {
        val body = requireNotNull(request.body).stringForTest()
        val decoded = Json.parseToJsonElement(body).jsonObject
        assertEquals(model, decoded.getValue("model").jsonPrimitive.content)
        val reasoning = decoded.getValue("reasoning").jsonObject
        assertEquals(effort, reasoning.getValue("effort").jsonPrimitive.content)
        assertTrue(reasoning.getValue("exclude").jsonPrimitive.boolean)
    }
}

private class ControllableCallFactory : Call.Factory {
    val calls = mutableListOf<ControllableCall>()

    override fun newCall(request: Request): Call = ControllableCall(request).also(calls::add)

    suspend fun awaitCallCount(expected: Int) {
        while (calls.size < expected) yield()
    }
}

private class ControllableCall(private val capturedRequest: Request) : Call {
    private var callback: Callback? = null
    private var executed = false
    private var canceled = false

    override fun request(): Request = capturedRequest
    override fun execute(): Response = error("Blocking execute must not be used")
    override fun enqueue(responseCallback: Callback) {
        executed = true
        callback = responseCallback
    }
    override fun cancel() { canceled = true }
    override fun isExecuted(): Boolean = executed
    override fun isCanceled(): Boolean = canceled
    override fun timeout(): Timeout = Timeout.NONE
    override fun clone(): Call = ControllableCall(capturedRequest)

    fun respondSuccess() = respond(200, successBody())

    fun respond(code: Int, body: String) {
        callback?.onResponse(
            this,
            Response.Builder()
                .request(capturedRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build(),
        ) ?: error("Call was not enqueued")
    }

    fun fail(error: IOException) {
        callback?.onFailure(this, error) ?: error("Call was not enqueued")
    }
}

private fun successBody(): String {
    val content = """{"records":[{"eventText":"读书","timestamp":"2026-07-11T10:00:00","startedAt":"2026-07-11T10:00:00","endedAt":"2026-07-11T10:00:00"}]}"""
    return completionBody(content)
}

private fun completionBody(content: String): String {
    return """{"choices":[{"message":{"role":"assistant","content":${Json.encodeToString(content)}}}]}"""
}

private fun okhttp3.RequestBody.stringForTest(): String {
    val buffer = okio.Buffer()
    writeTo(buffer)
    return buffer.readUtf8()
}
