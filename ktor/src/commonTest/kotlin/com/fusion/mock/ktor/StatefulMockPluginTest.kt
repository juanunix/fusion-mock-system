package com.fusion.mock.ktor

import com.fusion.mock.MockStrategy
import com.fusion.mock.StatefulMockProvider
import com.fusion.mock.TimedMockResponse
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StatefulMockPluginTest {

    @Test
    fun `test plugin intercepts Ktor request`() = runTest {
        val provider = StatefulMockProvider()
        provider.addMock(
            path = "/ktor",
            method = "GET",
            responses = listOf(TimedMockResponse(body = "ktor-mock", code = 200)),
            strategy = MockStrategy.FIFO
        )

        // The MockEngine here will only be hit if the plugin DOES NOT intercept
        val client = HttpClient(MockEngine { 
            respond("real-engine", HttpStatusCode.OK) 
        }) {
            installStatefulMock(provider)
        }

        val response: HttpResponse = client.get("https://example.com/ktor")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ktor-mock", response.bodyAsText())
    }

    @Test
    fun `test plugin proceeds to engine when no mock`() = runTest {
        val provider = StatefulMockProvider()
        
        val client = HttpClient(MockEngine { 
            respond("from-engine", HttpStatusCode.Accepted) 
        }) {
            installStatefulMock(provider)
        }

        val response: HttpResponse = client.get("https://example.com/other")
        assertEquals(HttpStatusCode.Accepted, response.status)
        assertEquals("from-engine", response.bodyAsText())
    }
}
