package com.example.royalnote.network

import com.example.royalnote.settings.OpenRouterRequestSettings
import com.example.royalnote.settings.OpenRouterSettingsProvider
import com.example.royalnote.ui.importFailureMessage
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterDispatcherAndBodyFailureTest {
    @Test
    fun importRequestConstructionAndBodyReadRunOnBlockingDispatcher() {
        val caller = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "caller-main") }
            .asCoroutineDispatcher()
        val blocking = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "network-worker") }
            .asCoroutineDispatcher()
        val body = ThreadRecordingBody(completionBodyForDispatcherTest("{\"records\":[]}"))
        val calls = ImmediateResponseCallFactory(body)
        try {
            runBlocking {
                withContext(caller) {
                    OpenRouterService(
                        settingsProvider = settingsProvider(),
                        client = calls,
                        blockingDispatcher = blocking,
                    ).parseRecords("旧录")
                }
            }

            assertTrue(calls.newCallThread.startsWith("network-worker"))
            assertTrue(body.readThread.startsWith("network-worker"))
        } finally {
            caller.close()
            blocking.close()
        }
    }

    @Test
    fun usageRequestConstructionAndBodyReadRunOnBlockingDispatcher() {
        val caller = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "caller-main") }
            .asCoroutineDispatcher()
        val blocking = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "network-worker") }
            .asCoroutineDispatcher()
        val body = ThreadRecordingBody("{\"data\":{\"usage_monthly\":12.34}}")
        val calls = ImmediateResponseCallFactory(body)
        try {
            val usage = runBlocking {
                withContext(caller) {
                    OpenRouterUsageService(
                        client = calls,
                        blockingDispatcher = blocking,
                    ).monthlyUsage("key")
                }
            }

            assertEquals(MonthlyUsage(12.34), usage)
            assertTrue(calls.newCallThread.startsWith("network-worker"))
            assertTrue(body.readThread.startsWith("network-worker"))
        } finally {
            caller.close()
            blocking.close()
        }
    }

    @Test
    fun midBodyIOExceptionRemainsTransportFailureWithNetworkMessage() {
        val calls = ImmediateResponseCallFactory(ThrowingBody())

        val error = runBlocking {
            runCatching {
                OpenRouterService(settingsProvider(), client = calls).parseRecords("旧录")
            }.exceptionOrNull()
        }

        assertTrue(error is IOException)
        assertFalse(error is OpenRouterResponseException)
        assertEquals("网络不通，稍后再试", importFailureMessage(requireNotNull(error)))
    }
}

private fun settingsProvider() = OpenRouterSettingsProvider {
    OpenRouterRequestSettings("key", "deepseek/deepseek-v4-pro", "high")
}

private fun completionBodyForDispatcherTest(content: String): String =
    """{"choices":[{"message":{"role":"assistant","content":${Json.encodeToString(content)}}}]}"""

private class ImmediateResponseCallFactory(
    private val responseBody: ResponseBody,
) : Call.Factory {
    lateinit var newCallThread: String

    override fun newCall(request: Request): Call {
        newCallThread = Thread.currentThread().name
        return ImmediateResponseCall(request, responseBody)
    }
}

private class ImmediateResponseCall(
    private val capturedRequest: Request,
    private val responseBody: ResponseBody,
) : Call {
    override fun request(): Request = capturedRequest
    override fun execute(): Response = error("Blocking execute must not be used")
    override fun enqueue(responseCallback: Callback) {
        responseCallback.onResponse(
            this,
            Response.Builder()
                .request(capturedRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("test")
                .body(responseBody)
                .build(),
        )
    }
    override fun cancel() = Unit
    override fun isExecuted(): Boolean = true
    override fun isCanceled(): Boolean = false
    override fun timeout(): Timeout = Timeout.NONE
    override fun clone(): Call = ImmediateResponseCall(capturedRequest, responseBody)
}

private class ThreadRecordingBody(private val content: String) : ResponseBody() {
    lateinit var readThread: String
    override fun contentType(): MediaType? = null
    override fun contentLength(): Long = content.toByteArray().size.toLong()
    override fun source(): BufferedSource {
        readThread = Thread.currentThread().name
        return Buffer().writeUtf8(content)
    }
}

private class ThrowingBody : ResponseBody() {
    override fun contentType(): MediaType? = null
    override fun contentLength(): Long = -1L
    override fun source(): BufferedSource = object : Source {
        private var firstRead = true
        override fun read(sink: Buffer, byteCount: Long): Long {
            if (firstRead) {
                firstRead = false
                sink.writeUtf8("{")
                return 1L
            }
            throw IOException("connection reset mid-body")
        }
        override fun timeout(): Timeout = Timeout.NONE
        override fun close() = Unit
    }.buffer()
}
