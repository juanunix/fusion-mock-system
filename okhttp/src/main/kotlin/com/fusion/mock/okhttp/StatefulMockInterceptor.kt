package com.fusion.mock.okhttp

import com.fusion.mock.StatefulMockProvider
import com.fusion.mock.TimedMockResponse
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * An OkHttp Interceptor that checks for registered mocks and returns them if found.
 * 
 * @param mockProvider The provider managing mocked responses.
 * @param debugLogging Optional flag to enable debug logging.
 */
class StatefulMockInterceptor(
    private val mockProvider: StatefulMockProvider,
    private val debugLogging: Boolean = false
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val method = request.method
        
        // Attempt to find a mock response
        val mock = mockProvider.getNextMock(path, method)
        
        if (mock != null) {
            if (debugLogging) {
                println("[Mock] Intercepted: $method $path -> Mocked Response (${mock.code})")
            }
            
            // Simulate network latency (blocking for OkHttp)
            if (mock.delayMs > 0) {
                Thread.sleep(mock.delayMs)
            }
            
            return createMockResponse(chain, mock)
        }
        
        if (debugLogging) {
            println("[Mock] No mock for: $method $path -> Proceeding to network")
        }
        
        return chain.proceed(request)
    }

    private fun createMockResponse(chain: Interceptor.Chain, mock: TimedMockResponse): Response {
        val request = chain.request()
        val contentType = mock.headers["Content-Type"] ?: "application/json"
        
        val responseBody = mock.body.toResponseBody(contentType.toMediaTypeOrNull())
        
        val responseBuilder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(mock.code)
            .message(if (mock.code >= 400) "Mock Error" else "Mock OK")
            .body(responseBody)
        
        // Add headers from the mock
        mock.headers.forEach { (name, value) ->
            responseBuilder.addHeader(name, value)
        }
        
        return responseBuilder.build()
    }
}
