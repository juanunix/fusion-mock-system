package com.fusion.mock.sample.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.fusion.mock.*
import com.fusion.mock.MockTemplates
import com.fusion.mock.ktor.installStatefulMock
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import androidx.compose.ui.window.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.selection.selectable

fun main() = application {
    val mockProvider = MockSystem.provider
    val client = remember {
        HttpClient {
            installStatefulMock(mockProvider, debugLogging = true)
        }
    }

    MaterialTheme(
        colors = darkColors(
            primary = Color(0xFF8AB4F8), // Google Blue (Dark Mode)
            secondary = Color(0xFF81C995), // Google Green (Dark Mode)
            error = Color(0xFFF28B82), // Google Red (Dark Mode)
            background = Color(0xFF000000), // Pure Black
            surface = Color(0xFF121212),
            onPrimary = Color.Black, // Contrast on soft blue
            onSurface = Color.White,
            onBackground = Color.White
        )
    ) {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Fusion Mock Dashboard (MVI/KMP)",
            state = rememberWindowState(width = 1100.dp, height = 800.dp)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                DesktopMockScreen(client, mockProvider)
            }
        }
    }

    // Setup initial mocks
    LaunchedEffect(Unit) {
        mockProvider.addMock(
            path = "/login",
            method = "POST",
            responses = listOf(
                TimedMockResponse("""{"status": "processing"}""", code = 202, delayMs = 1200),
                TimedMockResponse("""{"error": "bad_request"}""", code = 400, delayMs = 300),
                TimedMockResponse("""{"status": "success", "platform": "desktop"}""", code = 200, delayMs = 600)
            ),
            strategy = MockStrategy.FIFO
        )
    }

}

data class LogEntry(
    val reqIndex: Int,
    val timestamp: String,
    val elapsedSeconds: Long,
    val message: String,
    val isTransition: Boolean = false
)

// --- SOLID: Single Responsibility (Ktor Executor) ---
class KtorMockExecutor(private val client: HttpClient) : MockRequestExecutor {
    override suspend fun executeRequest(path: String): String {
        return try {
            val response: HttpResponse = client.post("https://api.example.com$path")
            "Status: ${response.status} | Body: ${response.bodyAsText()}"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

@Composable
fun DesktopMockScreen(client: HttpClient, mockProvider: StatefulMockProvider) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { MockViewModel(mockProvider, KtorMockExecutor(client), scope) }
    val state by viewModel.state.collectAsState()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    // Auto-scroll logic
    val logScrollState = rememberLazyListState()
    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            logScrollState.animateScrollToItem(state.logs.size - 1)
        }
    }

    if (state.showEditorDialog) {
        Dialog(
            onCloseRequest = { viewModel.processIntent(MockIntent.CloseEditor) },
            title = "Custom Mock Editor",
            state = androidx.compose.ui.window.rememberDialogState(width = 500.dp, height = 600.dp)
        ) {
            MaterialTheme(
                colors = darkColors(
                    background = Color(0xFF000000),
                    surface = Color(0xFF121212),
                    primary = Color(0xFF8AB4F8),
                    onSurface = Color.White
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Custom Mock Editor", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                        Text("Route: /login & /api/custom", style = MaterialTheme.typography.caption, color = Color.Gray)
                        Spacer(Modifier.height(12.dp))

                        Text("Load Presets:", style = MaterialTheme.typography.caption, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            listOf("FIFO", "POLLING", "RANDOM", "LATENCY").forEach { preset ->
                                OutlinedButton(
                                    onClick = {
                                        val list = when (preset) {
                                            "FIFO" -> MockTemplates.FIFO_SEQUENCE
                                            "POLLING" -> MockTemplates.POLLING_LOADING
                                            "RANDOM" -> MockTemplates.RANDOM_ERRORS
                                            else -> MockTemplates.LONG_LATENCY
                                        }.map { DraftMockState(it.body, (it.durationMs?.div(1000) ?: 5).toString()) }
                                        
                                        viewModel.processIntent(MockIntent.ClearLogs)
                                        viewModel.processIntent(MockIntent.SetDraftMocks(list))
                                    },
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Text(preset, fontSize = 10.sp)
                                }
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            val editorScrollState = rememberScrollState()
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(editorScrollState)) {
                                state.draftMocks.forEachIndexed { index, draft ->
                                    Card(
                                        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                                        backgroundColor = Color.DarkGray.copy(alpha = 0.3f)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text("Mock Item #${index + 1}", style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold, color = Color.Cyan)
                                                IconButton(onClick = { viewModel.processIntent(MockIntent.RemoveDraftMock(index)) }) {
                                                    Icon(Icons.Default.Delete, "Delete", tint = Color.Red, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                            OutlinedTextField(
                                                value = draft.body,
                                                onValueChange = { viewModel.processIntent(MockIntent.UpdateDraftMock(index, it, draft.durationS)) },
                                                label = { Text("Response JSON Body") },
                                                modifier = Modifier.fillMaxWidth(),
                                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp)
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = draft.durationS,
                                                onValueChange = { if (it.all { c -> c.isDigit() }) viewModel.processIntent(MockIntent.UpdateDraftMock(index, draft.body, it)) },
                                                label = { Text("Duration (seconds)") },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                                
                                Button(
                                    onClick = { viewModel.processIntent(MockIntent.AddDraftMock) },
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text("+ ADD RESPONSE STEP")
                                }
                            }
                            VerticalScrollbar(
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                adapter = rememberScrollbarAdapter(editorScrollState)
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { viewModel.processIntent(MockIntent.CloseEditor) }) {
                                Text("CANCEL", color = Color.Gray)
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = { viewModel.processIntent(MockIntent.ApplyCustomMocks) },
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50))
                            ) {
                                Text("APPLY GLOBALLY", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // LEFT COLUMN: CONTROLS
        Column(
            modifier = Modifier
                .weight(0.4f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Controls", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            // --- DYNAMIC MOCK EDITOR TRIGGER ---
            Button(
                onClick = { viewModel.processIntent(MockIntent.OpenEditor) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF34A853)) // Google Green
            ) {
                Text("OPEN DYNAMIC MOCK EDITOR ✏️", color = Color.White)
            }

            Text("Select Scenario:", style = MaterialTheme.typography.subtitle1, color = Color.Cyan)
            Spacer(Modifier.height(8.dp))
            
            val scenarioList = listOf("FIFO Sequence", "Polling / Refreshing", "Random Errors", "Long Latency", "Time-Based Transition")
            scenarioList.forEach { scenario ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (state.selectedScenario == scenario),
                                onClick = { viewModel.processIntent(MockIntent.SelectScenario(scenario)) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (state.selectedScenario == scenario),
                            onClick = { viewModel.processIntent(MockIntent.SelectScenario(scenario)) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(scenario, style = MaterialTheme.typography.body1)
                    }
                    
                    if (state.selectedScenario == scenario) {
                        Surface(
                            color = Color.DarkGray.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.padding(start = 40.dp, top = 4.dp)
                        ) {
                            Text(
                                text = when(scenario) {
                                    "FIFO Sequence" -> "Responde secuencialmente: 202 starting -> 401 error -> 200 success."
                                    "Polling / Refreshing" -> "Bucle infinito de estados: LOADING -> PROCESSING -> COMPLETED."
                                    "Random Errors" -> "El servidor falla aleatoriamente (500, 502, 503) para testear resiliencia."
                                    "Long Latency" -> "Simula una conexión lenta o timeout (8 segundos de espera)."
                                    "Time-Based Transition" -> "Cambia de respuesta automáticamente basándose en el tiempo transcurrido."
                                    else -> ""
                                },
                                style = MaterialTheme.typography.caption,
                                modifier = Modifier.padding(8.dp),
                                color = Color.LightGray
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.processIntent(MockIntent.TogglePolling) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (state.isPolling) Color(0xFFEA4335) else Color(0xFF4285F4) // Google Red / Blue
                )
            ) {
                val icon = if (state.isPolling) "⏹" else "▶"
                Text("$icon ${if (state.isPolling) "STOP POLLING" else "START AUTO-POLLING (2s)"}", color = Color.White, fontWeight = FontWeight.Bold)
            }

            if (!state.isPolling) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.processIntent(MockIntent.PerformSingleRequest) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text("EXECUTE SINGLE REQUEST")
                    }
                }
            }
        }

        // RIGHT COLUMN: TERMINAL LOGS
        Column(
            modifier = Modifier.weight(0.6f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Terminal Logs", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
                if (state.logs.isNotEmpty()) {
                    Row {
                        TextButton(onClick = { viewModel.processIntent(MockIntent.ClearLogs) }) {
                            Text("CLEAR", color = Color.Gray)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            val fullLog = state.logs.joinToString("\n") { "[${it.timestamp}] (+${it.elapsedSeconds}s) ${it.message}" }
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(fullLog))
                        }) {
                            Text("COPY ALL")
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, Color.Gray.copy(alpha = 0.5f), MaterialTheme.shapes.medium),
                shape = MaterialTheme.shapes.medium,
                color = Color.Black.copy(alpha = 0.9f)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    state = logScrollState,
                    reverseLayout = false // standard terminal
                ) {
                    items(state.logs) { log ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .background(
                                    if (log.isTransition) Color.Blue.copy(alpha = 0.3f) 
                                    else Color.Transparent
                                )
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = buildString {
                                    if (log.reqIndex > 0) append("#${log.reqIndex} ")
                                    append("[${log.timestamp} | +${log.elapsedSeconds}s] ")
                                    append(log.message)
                                },
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (log.isTransition) Color.Cyan else Color.LightGray
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
