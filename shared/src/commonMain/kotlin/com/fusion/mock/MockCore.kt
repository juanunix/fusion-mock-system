package com.fusion.mock

/**
 * Expected platform-specific time provider.
 */
expect fun currentTimeMillis(): Long

expect fun formatTime(millis: Long): String

/**
 * Expected platform-specific thread-safe map.
 */
expect fun <K, V> createConcurrentMap(): MutableMap<K, V>

/**
 * Strategy for selecting the next mock response.
 */
enum class MockStrategy {
    FIFO,   // First In, First Out (Sequential)
    LOOP,   // Sequential and loops back to start
    RANDOM  // Random selection from the list
}

/**
 * Represents a mocked HTTP response with simulated latency.
 */
data class TimedMockResponse(
    val body: String,
    val code: Int = 200,
    val delayMs: Long = 0,
    val headers: Map<String, String> = emptyMap(),
    val durationMs: Long? = null // How long this mock stays active before moving to the next one
)

/**
 * Internal state to track an active mock's time.
 */
private data class ActiveMockState(
    val response: TimedMockResponse,
    val activatedAt: Long
)

/**
 * Simple key for initial request matching.
 */
data class RequestKey(
    val path: String,
    val method: String
)

/**
 * Interface for flexible request matching.
 */
interface RequestMatcher {
    fun matches(path: String, method: String, headers: Map<String, String>): Boolean
}

/**
 * Default implementation of RequestMatcher using path and method.
 */
class DefaultRequestMatcher(
    private val targetPath: String,
    private val targetMethod: String
) : RequestMatcher {
    override fun matches(path: String, method: String, headers: Map<String, String>): Boolean {
        val cleanPath = path.substringBefore("?")
        return cleanPath.equals(targetPath, ignoreCase = true) && 
               method.equals(targetMethod, ignoreCase = true)
    }
}

/**
 * Thread-safe provider that manages mocked responses.
 * Supports FIFO queues, looping, and time-based transitions.
 */
class StatefulMockProvider {
    private val mockQueueMap = createConcurrentMap<RequestKey, MutableList<TimedMockResponse>>()
    private val loopingMocks = createConcurrentMap<RequestKey, List<TimedMockResponse>>()
    private val randomMocks = createConcurrentMap<RequestKey, List<TimedMockResponse>>()
    private val loopPointers = createConcurrentMap<RequestKey, Int>()
    
    // Tracks which mock is currently "active" and for how long
    private val activeStateMap = createConcurrentMap<RequestKey, ActiveMockState>()
    
    private var isEnabled = true

    /**
     * Adds a sequence of mocks for a specific endpoint.
     */
    fun addMock(
        path: String,
        method: String,
        responses: List<TimedMockResponse>,
        strategy: MockStrategy = MockStrategy.FIFO
    ) {
        val key = RequestKey(path, method)
        // Clean up previous registration
        mockQueueMap.remove(key)
        loopingMocks.remove(key)
        randomMocks.remove(key)
        loopPointers.remove(key)
        activeStateMap.remove(key)

        when (strategy) {
            MockStrategy.FIFO -> {
                mockQueueMap[key] = responses.toMutableList()
            }
            MockStrategy.LOOP -> {
                loopingMocks[key] = responses
                loopPointers[key] = 0
            }
            MockStrategy.RANDOM -> {
                randomMocks[key] = responses
            }
        }
    }

    /**
     * Retrieves the next mock response for the given request.
     * Respects durationMs if provided to keep the same mock active for a period.
     */
    fun getNextMock(path: String, method: String): TimedMockResponse? {
        if (!isEnabled) return null
        
        val key = RequestKey(path, method)
        val now = currentTimeMillis()
        
        // 1. Check if current active mock is still valid by time
        val currentState = activeStateMap[key]
        if (currentState != null) {
            val duration = currentState.response.durationMs
            if (duration == null || (now - currentState.activatedAt) < duration) {
                // Return same mock if no duration limit or time hasn't expired
                // For FIFO with no duration, we always move to next, so this only hits if duration is set
                if (duration != null) return currentState.response
            }
        }

        // 2. Fetch the next mock from the registered collections
        val nextResponse = findNextCandidate(key)
        
        if (nextResponse != null) {
            // Save as active state
            activeStateMap[key] = ActiveMockState(nextResponse, now)
        } else {
            activeStateMap.remove(key)
        }
        
        return nextResponse
    }

    private fun findNextCandidate(key: RequestKey): TimedMockResponse? {
        // A. FIFO
        val queue = mockQueueMap[key]
        if (queue != null && queue.isNotEmpty()) {
            return queue.removeAt(0)
        }
        
        // B. LOOP
        val loopList = loopingMocks[key]
        if (loopList != null && loopList.isNotEmpty()) {
            val currentIndex = loopPointers[key] ?: 0
            val response = loopList[currentIndex]
            loopPointers[key] = (currentIndex + 1) % loopList.size
            return response
        }

        // C. RANDOM
        val randomList = randomMocks[key]
        if (randomList != null && randomList.isNotEmpty()) {
            return randomList.random()
        }
        
        return null
    }

    fun clearMock(path: String, method: String) {
        val key = RequestKey(path, method)
        mockQueueMap.remove(key)
        loopingMocks.remove(key)
        randomMocks.remove(key)
        loopPointers.remove(key)
        activeStateMap.remove(key)
    }

    fun clearAll() {
        mockQueueMap.clear()
        loopingMocks.clear()
        randomMocks.clear()
        loopPointers.clear()
        activeStateMap.clear()
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }
}
