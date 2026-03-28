package com.fusion.mock

object MockTemplates {
    val FIFO_SEQUENCE = listOf(
        TimedMockResponse("""{"status": "starting"}""", 202, 1200),
        TimedMockResponse("""{"error": "unauthorized"}""", 401, 300),
        TimedMockResponse("""{"status": "success"}""", 200, 600)
    )

    val POLLING_LOADING = listOf(
        TimedMockResponse("""{"state": "LOADING", "step": "1/3"}""", 200, 400, emptyMap(), 10000),
        TimedMockResponse("""{"state": "PROCESSING", "step": "2/3"}""", 200, 400, emptyMap(), 10000),
        TimedMockResponse("""{"state": "COMPLETED", "step": "3/3"}""", 200, 400, emptyMap(), 10000)
    )

    val RANDOM_ERRORS = listOf(
        TimedMockResponse("""{"status": "ok"}""", 200),
        TimedMockResponse("""{"error": "Internal Server Error"}""", 500),
        TimedMockResponse("""{"error": "Bad Gateway"}""", 502),
        TimedMockResponse("""{"error": "Forbidden"}""", 403)
    )

    val LONG_LATENCY = listOf(
        TimedMockResponse("""{"status": "timeout_simulation"}""", 200, 8000)
    )
}
