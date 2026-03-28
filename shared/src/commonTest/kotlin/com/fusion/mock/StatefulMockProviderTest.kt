package com.fusion.mock

import kotlin.test.*

class StatefulMockProviderTest {
    private lateinit var provider: StatefulMockProvider

    @BeforeTest
    fun setup() {
        provider = StatefulMockProvider()
    }

    @Test
    fun `test sequential FIFO mocks`() {
        provider.addMock(
            path = "/test",
            method = "GET",
            responses = listOf(
                TimedMockResponse(body = "1", code = 200),
                TimedMockResponse(body = "2", code = 400)
            ),
            strategy = MockStrategy.FIFO
        )

        val first = provider.getNextMock("/test", "GET")
        assertEquals("1", first?.body)
        assertEquals(200, first?.code)

        val second = provider.getNextMock("/test", "GET")
        assertEquals("2", second?.body)
        assertEquals(400, second?.code)

        val third = provider.getNextMock("/test", "GET")
        assertNull(third)
    }

    @Test
    fun `test looping mocks`() {
        provider.addMock(
            path = "/loop",
            method = "GET",
            responses = listOf(
                TimedMockResponse(body = "A"),
                TimedMockResponse(body = "B")
            ),
            strategy = MockStrategy.LOOP
        )

        assertEquals("A", provider.getNextMock("/loop", "GET")?.body)
        assertEquals("B", provider.getNextMock("/loop", "GET")?.body)
        assertEquals("A", provider.getNextMock("/loop", "GET")?.body)
        assertEquals("B", provider.getNextMock("/loop", "GET")?.body)
    }

    @Test
    fun `test duration based persistence`() {
        val duration = 1000L // 1 second
        provider.addMock(
            path = "/time",
            method = "GET",
            responses = listOf(
                TimedMockResponse("Response 1", durationMs = duration),
                TimedMockResponse("Response 2")
            )
        )
        
        // First call
        val first = provider.getNextMock("/time", "GET")
        assertEquals("Response 1", first?.body)
        
        // Call quickly after (within duration)
        val second = provider.getNextMock("/time", "GET")
        assertEquals("Response 1", second?.body, "Should still be Response 1 within duration")
        
        // Wait for duration to pass
        Thread.sleep(duration + 100)
        
        val third = provider.getNextMock("/time", "GET")
        assertEquals("Response 2", third?.body, "Should move to Response 2 after duration")
    }

    @Test
    fun `test clear mocks`() {
        provider.addMock("/test", "GET", listOf(TimedMockResponse("body")))
        provider.clearMock("/test", "GET")
        
        assertNull(provider.getNextMock("/test", "GET"))
    }

    @Test
    fun `test global disabled`() {
        provider.addMock("/test", "GET", listOf(TimedMockResponse("body")))
        provider.setEnabled(false)
        
        assertNull(provider.getNextMock("/test", "GET"))
        
        provider.setEnabled(true)
        assertNotNull(provider.getNextMock("/test", "GET"))
    }
}
