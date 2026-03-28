package com.fusion.mock.okhttp

import com.fusion.mock.MockStrategy
import com.fusion.mock.StatefulMockProvider
import com.fusion.mock.TimedMockResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StatefulMockInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var provider: StatefulMockProvider
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        
        provider = StatefulMockProvider()
        client = OkHttpClient.Builder()
            .addInterceptor(StatefulMockInterceptor(provider))
            .build()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `test interceptor returns mock when registered`() {
        provider.addMock(
            path = "/login",
            method = "POST",
            responses = listOf(TimedMockResponse(body = "mocked", code = 201)),
            strategy = MockStrategy.FIFO
        )

        val request = Request.Builder()
            .url(server.url("/login"))
            .post(okhttp3.RequestBody.create(null, ""))
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(201, response.code)
            assertEquals("mocked", response.body?.string())
        }
    }

    @Test
    fun `test interceptor proceeds to network when no mock registered`() {
        server.enqueue(MockResponse().setBody("real").setResponseCode(200))

        val request = Request.Builder()
            .url(server.url("/real-api"))
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("real", response.body?.string())
        }
    }
}
