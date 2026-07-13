package com.example.royalnote.network

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class OpenRouterUsageServiceTest {
    @Test
    fun monthlyUsageUsesBearerGetAndParsesUsageMonthly() = runTest {
        val calls = FakeCallFactory(code = 200, body = """{"data":{"usage_monthly":12.34}}""")
        val service = OpenRouterUsageService(calls)

        assertEquals(MonthlyUsage(12.34), service.monthlyUsage("sk-or-v1-test"))
        assertEquals("GET", calls.request.method)
        assertEquals(OpenRouterConfig.CURRENT_KEY_URL, calls.request.url.toString())
        assertEquals("Bearer sk-or-v1-test", calls.request.header("Authorization"))
    }

    @Test
    fun unauthorizedResponseThrowsDistinctError() = runTest {
        val service = OpenRouterUsageService(FakeCallFactory(401, "{}"))

        assertFailsWith<InvalidOpenRouterApiKeyException> {
            service.monthlyUsage("bad-key")
        }
    }

    @Test
    fun serverFailureThrowsIOException() = runTest {
        val service = OpenRouterUsageService(FakeCallFactory(500, "{}"))

        assertFailsWith<IOException> {
            service.monthlyUsage("sk-or-v1-test")
        }
    }
}

private suspend inline fun <reified T : Throwable> assertFailsWith(noinline block: suspend () -> Unit) {
    try {
        block()
        fail("Expected ${T::class.java.simpleName}")
    } catch (error: Throwable) {
        if (error !is T) throw error
    }
}

private class FakeCallFactory(
    private val code: Int,
    private val body: String,
) : Call.Factory {
    lateinit var request: Request

    override fun newCall(request: Request): Call {
        this.request = request
        return FakeCall(request, code, body)
    }
}

private class FakeCall(
    private val capturedRequest: Request,
    private val code: Int,
    private val body: String,
) : Call {
    private var executed = false
    private var canceled = false

    override fun request(): Request = capturedRequest

    override fun execute(): Response {
        executed = true
        return response()
    }

    override fun enqueue(responseCallback: Callback) {
        executed = true
        responseCallback.onResponse(this, response())
    }

    override fun cancel() {
        canceled = true
    }

    override fun isExecuted(): Boolean = executed
    override fun isCanceled(): Boolean = canceled
    override fun timeout(): Timeout = Timeout.NONE
    override fun clone(): Call = FakeCall(capturedRequest, code, body)

    private fun response(): Response = Response.Builder()
        .request(capturedRequest)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()
}
