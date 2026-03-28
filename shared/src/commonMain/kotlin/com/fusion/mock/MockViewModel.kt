package com.fusion.mock

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MockViewModel(
    private val mockProvider: StatefulMockProvider,
    private val executor: MockRequestExecutor,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(MockDashboardState())
    val state: StateFlow<MockDashboardState> = _state.asStateFlow()

    private var pollingJob: Job? = null
    private var lastBody: String? = null

    init {
        // Initial setup
        setupScenario(_state.value.selectedScenario)
    }

    fun processIntent(intent: MockIntent) {
        when (intent) {
            is MockIntent.SelectScenario -> {
                _state.update { it.copy(selectedScenario = intent.scenario, logs = emptyList()) }
                setupScenario(intent.scenario)
            }
            MockIntent.TogglePolling -> {
                val newPolling = !_state.value.isPolling
                _state.update { it.copy(isPolling = newPolling, pollingStartTime = System.currentTimeMillis(), requestCount = 0) }
                if (newPolling) startPolling() else stopPolling()
            }
            MockIntent.ClearLogs -> _state.update { it.copy(logs = emptyList()) }
            MockIntent.OpenEditor -> _state.update { it.copy(showEditorDialog = true) }
            MockIntent.CloseEditor -> _state.update { it.copy(showEditorDialog = false) }
            is MockIntent.SetDraftMocks -> {
                _state.value = _state.value.copy(draftMocks = intent.mocks)
            }
            is MockIntent.AddDraftMock -> {
                val current = _state.value.draftMocks.toMutableList()
                current.add(DraftMockState("""{"status": "NEW"}""", "5"))
                _state.update { it.copy(draftMocks = current) }
            }
            is MockIntent.RemoveDraftMock -> {
                val current = _state.value.draftMocks.toMutableList()
                if (current.size > 1) {
                    current.removeAt(intent.index)
                    _state.update { it.copy(draftMocks = current) }
                }
            }
            is MockIntent.UpdateDraftMock -> {
                val current = _state.value.draftMocks.toMutableList()
                current[intent.index] = DraftMockState(intent.body, intent.durationS)
                _state.update { it.copy(draftMocks = current) }
            }
            MockIntent.ApplyCustomMocks -> {
                val responses = _state.value.draftMocks.map { 
                    TimedMockResponse(body = it.body, durationMs = (it.durationS.toLongOrNull() ?: 5L) * 1000L)
                }
                if (responses.isNotEmpty()) {
                    mockProvider.addMock("/login", "POST", responses, MockStrategy.LOOP)
                    mockProvider.addMock("/api/custom", "POST", responses, MockStrategy.LOOP)
                    _state.update { it.copy(selectedScenario = "Custom Editor (LOOP)", logs = emptyList(), showEditorDialog = false) }
                }
            }
            MockIntent.PerformSingleRequest -> {
                scope.launch {
                    _state.update { it.copy(isLoading = true) }
                    val path = if (_state.value.selectedScenario == "Custom Editor (LOOP)") "/api/custom" else "/login"
                    val result = executor.executeRequest(path)
                    addLog(result)
                    _state.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            lastBody = null
            while (isActive) {
                val loopStart = System.currentTimeMillis()
                val path = if (_state.value.selectedScenario == "Custom Editor (LOOP)") "/api/custom" else "/login"
                val result = executor.executeRequest(path)
                
                val elapsed = (System.currentTimeMillis() - _state.value.pollingStartTime) / 1000
                
                if (lastBody != null && result != lastBody) {
                    addLog("🔄 >>> NEXT MOCK ACTIVATED <<< 🔄", isTransition = true, elapsed = elapsed)
                }
                lastBody = result
                
                _state.update { it.copy(requestCount = it.requestCount + 1) }
                addLog(result, elapsed = elapsed, reqIndex = _state.value.requestCount)
                
                val loopDuration = System.currentTimeMillis() - loopStart
                delay((2000 - loopDuration).coerceAtLeast(0))
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun addLog(message: String, isTransition: Boolean = false, elapsed: Long = 0, reqIndex: Int = 0) {
        val entry = MockLogEntry(
            reqIndex = reqIndex,
            timestamp = currentTimeString(),
            elapsedSeconds = elapsed,
            message = message,
            isTransition = isTransition
        )
        _state.update { it.copy(logs = it.logs + entry) }
    }

    private fun currentTimeString(): String {
        return formatTime(currentTimeMillis())
    }

    private fun setupScenario(scenario: String) {
        mockProvider.clearAll()
        when (scenario) {
            "FIFO Sequence" -> {
                mockProvider.addMock("/login", "POST", MockTemplates.FIFO_SEQUENCE, MockStrategy.FIFO)
            }
            "Polling / Refreshing" -> {
                mockProvider.addMock("/login", "POST", MockTemplates.POLLING_LOADING, MockStrategy.LOOP)
            }
            "Random Errors" -> {
                mockProvider.addMock("/login", "POST", MockTemplates.RANDOM_ERRORS, MockStrategy.RANDOM)
            }
            "Long Latency" -> {
                mockProvider.addMock("/login", "POST", MockTemplates.LONG_LATENCY)
            }
            "Time-Based Transition" -> {
                mockProvider.addMock("/login", "POST", listOf(
                    TimedMockResponse("""{"step": "1/3"}""", 200, 0, emptyMap(), 10000),
                    TimedMockResponse("""{"step": "2/3"}""", 200, 0, emptyMap(), 10000),
                    TimedMockResponse("""{"step": "3/3"}""", 200)
                ), MockStrategy.FIFO)
            }
        }
    }
}
