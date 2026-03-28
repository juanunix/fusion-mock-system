import com.fusion.mock.StatefulMockProvider
import com.fusion.mock.TimedMockResponse
import com.fusion.mock.okhttp.StatefulMockInterceptor
import com.fusion.mock.ktor.installStatefulMock
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request

fun main() {
    // 1. Initialize the shared provider
    val mockProvider = StatefulMockProvider()
    
    // 2. Register sequential mocks for /login
    mockProvider.addMock(
        path = "/login",
        method = "POST",
        responses = listOf(
            // State 1: Loading/Processing (2s delay)
            TimedMockResponse(
                body = """{"status": "processing", "message": "Please wait..."}""",
                code = 202,
                delayMs = 2000
            ),
            // State 2: Error (401 Unauthorized)
            TimedMockResponse(
                body = """{"error": "invalid_credentials"}""",
                code = 401
            ),
            // State 3: Success
            TimedMockResponse(
                body = """{"status": "success", "token": "abc-123-xyz"}""",
                code = 200
            )
        ),
        strategy = com.fusion.mock.MockStrategy.FIFO
    )

    println("--- RUNNING OKHTTP EXAMPLE ---")
    runOkHttpExample(mockProvider)

    println("\n--- RUNNING KTOR EXAMPLE ---")
    runKtorExample(mockProvider)
}

/**
 * Demonstrates OkHttp integration.
 */
fun runOkHttpExample(provider: StatefulMockProvider) {
    val client = OkHttpClient.Builder()
        .addInterceptor(StatefulMockInterceptor(provider, debugLogging = true))
        .build()

    val request = Request.Builder()
        .url("https://api.example.com/login")
        .post(okhttp3.RequestBody.create(null, ""))
        .build()

    // We'll call it 3 times to consume the queue
    repeat(3) { i ->
        println("\nOkHttp Call #${i + 1} starting...")
        val startTime = System.currentTimeMillis()
        client.newCall(request).execute().use { response ->
            val duration = System.currentTimeMillis() - startTime
            println("Response: ${response.code} | Body: ${response.body?.string()} | Took: ${duration}ms")
        }
    }
}

/**
 * Demonstrates Ktor integration.
 */
fun runKtorExample(provider: StatefulMockProvider) {
    // Re-filling the queue for the Ktor example since OkHttp consumed it
    provider.addMock(
        path = "/login",
        method = "POST",
        responses = listOf(
            TimedMockResponse("""{"status": "processing"}""", code = 202, delayMs = 1000),
            TimedMockResponse("""{"error": "unauthorized"}""", code = 401),
            TimedMockResponse("""{"status": "success"}""", code = 200)
        )
    )

    val client = HttpClient {
        installStatefulMock(provider, debugLogging = true)
    }

    runBlocking {
        repeat(3) { i ->
            println("\nKtor Call #${i + 1} starting...")
            val startTime = System.currentTimeMillis()
            val response: HttpResponse = client.post("https://api.example.com/login")
            val duration = System.currentTimeMillis() - startTime
            println("Response: ${response.status} | Body: ${response.bodyAsText()} | Took: ${duration}ms")
        }
    }
}
