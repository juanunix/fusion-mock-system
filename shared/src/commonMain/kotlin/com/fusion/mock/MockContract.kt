package com.fusion.mock



data class MockLogEntry(
    val reqIndex: Int,
    val timestamp: String,
    val elapsedSeconds: Long,
    val message: String,
    val isTransition: Boolean = false
)

data class DraftMockState(
    var body: String,
    var durationS: String
)

data class MockDashboardState(
    val logs: List<MockLogEntry> = emptyList(),
    val isPolling: Boolean = false,
    val isLoading: Boolean = false,
    val selectedScenario: String = "FIFO Sequence",
    val showEditorDialog: Boolean = false,
    val draftMocks: List<DraftMockState> = listOf(DraftMockState("""{"status": "CUSTOM_1"}""", "5")),
    val pollingStartTime: Long = 0L,
    val requestCount: Int = 0
)

sealed interface MockIntent {
    data class SelectScenario(val scenario: String) : MockIntent
    object TogglePolling : MockIntent
    object ClearLogs : MockIntent
    data class UpdateDraftMock(val index: Int, val body: String, val durationS: String) : MockIntent
    data class SetDraftMocks(val mocks: List<DraftMockState>) : MockIntent
    object AddDraftMock : MockIntent
    data class RemoveDraftMock(val index: Int) : MockIntent
    object ApplyCustomMocks : MockIntent
    object OpenEditor : MockIntent
    object CloseEditor : MockIntent
    object PerformSingleRequest : MockIntent
}

interface MockRequestExecutor {
    suspend fun executeRequest(path: String): String
}
