package com.example.royalnote.network

import com.example.royalnote.settings.OpenRouterRequestSettings
import com.example.royalnote.settings.OpenRouterSettingsProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterInFlightCancellationTest {
    @Test
    fun importCancellationDuringBodyReadCancelsCallClosesBodyAndPublishesNothing() = runBlocking {
        val body = BlockingAfterContentBody(completionBodyForCancellation("{\"records\":[]}"))
        val calls = BlockingBodyCallFactory(body)
        val service = OpenRouterService(
            settingsProvider = OpenRouterSettingsProvider {
                OpenRouterRequestSettings("key", "deepseek/deepseek-v4-pro", "high")
            },
            client = calls,
        )
        var result: ParsedRecords? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) { result = service.parseRecords("旧录") }
        assertTrue(body.awaitReadStarted())

        val completedPromptly = cancelPromptly(job, body)

        assertTrue(completedPromptly)
        assertTrue(calls.call.isCanceled())
        assertTrue(body.closed.get())
        assertNull(result)
        assertTrue(job.isCancelled)
    }

    @Test
    fun usageCancellationDuringBodyReadCancelsCallClosesBodyAndPublishesNothing() = runBlocking {
        val body = BlockingAfterContentBody("{\"data\":{\"usage_monthly\":12.34}}")
        val calls = BlockingBodyCallFactory(body)
        val service = OpenRouterUsageService(client = calls)
        var result: MonthlyUsage? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) { result = service.monthlyUsage("key") }
        assertTrue(body.awaitReadStarted())

        val completedPromptly = cancelPromptly(job, body)

        assertTrue(completedPromptly)
        assertTrue(calls.call.isCanceled())
        assertTrue(body.closed.get())
        assertNull(result)
        assertTrue(job.isCancelled)
    }
}

private suspend fun cancelPromptly(
    job: kotlinx.coroutines.Job,
    body: BlockingAfterContentBody,
): Boolean {
    return try {
        withTimeout(750) { job.cancelAndJoin() }
        true
    } catch (_: TimeoutCancellationException) {
        false
    } finally {
        body.release()
        job.join()
    }
}

private fun completionBodyForCancellation(content: String): String =
    """{"choices":[{"message":{"role":"assistant","content":${Json.encodeToString(content)}}}]}"""

private class BlockingBodyCallFactory(private val body: ResponseBody) : Call.Factory {
    lateinit var call: BlockingBodyCall
    override fun newCall(request: Request): Call = BlockingBodyCall(request, body).also { call = it }
}

private class BlockingBodyCall(
    private val capturedRequest: Request,
    private val body: ResponseBody,
) : Call {
    private val canceled = AtomicBoolean(false)
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
                .body(body)
                .build(),
        )
    }
    override fun cancel() { canceled.set(true) }
    override fun isExecuted(): Boolean = true
    override fun isCanceled(): Boolean = canceled.get()
    override fun timeout(): Timeout = Timeout.NONE
    override fun clone(): Call = BlockingBodyCall(capturedRequest, body)
}

private class BlockingAfterContentBody(content: String) : ResponseBody() {
    private val bytes = content.toByteArray()
    private val readStarted = CountDownLatch(1)
    private val releaseRead = CountDownLatch(1)
    val closed = AtomicBoolean(false)

    override fun contentType(): MediaType? = null
    override fun contentLength(): Long = -1L
    override fun source(): BufferedSource {
        readStarted.countDown()
        return object : Source {
        private var emitted = false
        override fun read(sink: Buffer, byteCount: Long): Long {
            if (emitted) return -1L
            releaseRead.await()
            emitted = true
            sink.write(bytes)
            return bytes.size.toLong()
        }
        override fun timeout(): Timeout = Timeout.NONE
        override fun close() = Unit
        }.buffer()
    }

    fun awaitReadStarted(): Boolean = readStarted.await(2, TimeUnit.SECONDS)
    fun release() = releaseRead.countDown()

    override fun close() {
        closed.set(true)
        release()
        super.close()
    }
}
