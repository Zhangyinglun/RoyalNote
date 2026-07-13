package com.example.royalnote.network

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterUsageCancellationTest {
    @Test
    fun cancellationCancelsHttpCallAndCannotPublishStaleUsage() = runTest {
        val factory = PendingUsageCallFactory()
        val service = OpenRouterUsageService(factory)
        var usage: MonthlyUsage? = null
        val job = launch { usage = service.monthlyUsage("key") }
        factory.awaitCall()

        job.cancelAndJoin()

        assertTrue(factory.call.isCanceled())
        assertNull(usage)
        assertTrue(job.isCancelled)
    }
}

private class PendingUsageCallFactory : Call.Factory {
    lateinit var call: PendingUsageCall
    override fun newCall(request: Request): Call = PendingUsageCall(request).also { call = it }
    suspend fun awaitCall() {
        while (!::call.isInitialized) yield()
    }
}

private class PendingUsageCall(private val capturedRequest: Request) : Call {
    private var canceled = false
    override fun request(): Request = capturedRequest
    override fun execute(): Response = error("Blocking execute must not be used")
    override fun enqueue(responseCallback: Callback) = Unit
    override fun cancel() { canceled = true }
    override fun isExecuted(): Boolean = true
    override fun isCanceled(): Boolean = canceled
    override fun timeout(): Timeout = Timeout.NONE
    override fun clone(): Call = PendingUsageCall(capturedRequest)
}
