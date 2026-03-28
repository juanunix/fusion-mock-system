package com.fusion.mock.ktor

import com.fusion.mock.StatefulMockProvider
import com.fusion.mock.TimedMockResponse
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay

/**
 * A Ktor Client Plugin that intercepts requests and returns mocked responses.
 * 
 * @param provider The provider managing mocked responses.
 * @param debugLogging Optional flag for debug console output.
 */
class StatefulMockPlugin(
    private val provider: StatefulMockProvider,
    private val debugLogging: Boolean = false
) {
    // Internal mock engine used ONLY to generate valid HttpClientCall instances for mocked responses
    private val internalMockEngine = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                // This handler is only called when we manually trigger it below
                error("Should not be called directly")
            }
        }
    }

    class Config {
        var provider: StatefulMockProvider? = null
        var debugLogging: Boolean = false
    }

    companion object Plugin : HttpClientPlugin<Config, StatefulMockPlugin> {
        override val key: AttributeKey<StatefulMockPlugin> = AttributeKey("StatefulMockPlugin")

        override fun prepare(block: Config.() -> Unit): StatefulMockPlugin {
            val config = Config().apply(block)
            val provider = config.provider ?: throw IllegalArgumentException("MockProvider must be provided")
            return StatefulMockPlugin(provider, config.debugLogging)
        }

        override fun install(plugin: StatefulMockPlugin, scope: HttpClient) {
            scope.plugin(HttpSend).intercept { requestBuilder ->
                val path = requestBuilder.url.encodedPath
                val method = requestBuilder.method.value
                
                val mock = plugin.provider.getNextMock(path, method)
                
                if (mock != null) {
                    if (plugin.debugLogging) {
                        println("[Mock Ktor] Intercepted: $method $path -> Mocked Response (${mock.code})")
                    }
                    
                    if (mock.delayMs > 0) {
                        delay(mock.delayMs)
                    }
                    
                    // Generate the mock call using a temporary MockEngine to stay within public APIs
                    val tempClient = HttpClient(MockEngine) {
                        engine {
                            addHandler { _ ->
                                respond(
                                    content = ByteReadChannel(mock.body),
                                    status = HttpStatusCode.fromValue(mock.code),
                                    headers = Headers.build {
                                        mock.headers.forEach { (name, value) -> append(name, value) }
                                        if (!contains(HttpHeaders.ContentType)) {
                                            append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                        }
                                    }
                                )
                            }
                        }
                    }
                    tempClient.request(requestBuilder).call
                } else {
                    if (plugin.debugLogging) {
                        println("[Mock Ktor] No mock for: $method $path -> Proceeding to network")
                    }
                    execute(requestBuilder)
                }
            }
        }
    }
}

/**
 * Helper function to install the StatefulMockPlugin easily.
 */
fun HttpClientConfig<*>.installStatefulMock(
    provider: StatefulMockProvider,
    debugLogging: Boolean = false
) {
    install(StatefulMockPlugin) {
        this.provider = provider
        this.debugLogging = debugLogging
    }
}
