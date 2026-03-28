package com.fusion.mock.sample.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fusion.mock.*
import com.fusion.mock.okhttp.StatefulMockInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

class MainActivity : ComponentActivity() {
    private val mockViewModel by lazy {
        MockViewModel(MockSystem.provider, OkHttpMockExecutor(client), MockSystem.scope)
    }
    
    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(StatefulMockInterceptor(MockSystem.provider, debugLogging = true))
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup initial mocks via Singleton
        MockSystem.provider.addMock(
            path = "/login",
            method = "POST",
            responses = listOf(
                TimedMockResponse("""{"status": "processing"}""", code = 202, delayMs = 1500),
                TimedMockResponse("""{"error": "unauthorized"}""", code = 401, delayMs = 500),
                TimedMockResponse("""{"status": "success", "token": "android-token-123"}""", code = 200, delayMs = 800)
            ),
            strategy = MockStrategy.FIFO
        )

        val googleBlue = Color(0xFF8AB4F8) 
        val googleRed = Color(0xFFF28B82)  
        val googleYellow = Color(0xFFFDE293)
        val googleGreen = Color(0xFF81C995) 

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = googleBlue,
                    onPrimary = Color.Black,
                    secondary = googleGreen,
                    onSecondary = Color.Black,
                    tertiary = googleYellow,
                    error = googleRed,
                    surface = Color(0xFF1E1E1E),
                    background = Color(0xFF000000), 
                    surfaceVariant = Color(0xFF2C2C2C),
                    onSurfaceVariant = Color.White
                ),
                shapes = Shapes(
                    small = RoundedCornerShape(12.dp),
                    medium = RoundedCornerShape(16.dp),
                    large = RoundedCornerShape(24.dp)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    DashboardScreen(mockViewModel, client)
                }
            }
        }
    }
}

class OkHttpMockExecutor(private val client: OkHttpClient) : MockRequestExecutor {
    override suspend fun executeRequest(path: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.example.com$path")
            .post(RequestBody.create(null, ByteArray(0)))
            .build()
            
        val startTime = System.currentTimeMillis()
        try {
            client.newCall(request).execute().use { response ->
                val duration = System.currentTimeMillis() - startTime
                "Code: ${response.code} | Time: ${duration}ms\nBody: ${response.body?.string()}"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MockViewModel, client: OkHttpClient) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val logScrollState = rememberLazyListState()
    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            logScrollState.animateScrollToItem(state.logs.size - 1)
        }
    }

    if (state.showEditorDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.processIntent(MockIntent.CloseEditor) },
            confirmButton = {
                Button(onClick = { viewModel.processIntent(MockIntent.ApplyCustomMocks) }) {
                    Text("APPLY GLOBALLY")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.processIntent(MockIntent.CloseEditor) }) {
                    Text("CANCEL")
                }
            },
            title = { Text("Custom Mock Editor") },
            text = {
                val hScrollState = rememberScrollState()
                val vScrollState = rememberScrollState()
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp)) {
                    Text("Presets: ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.horizontalScroll(hScrollState).padding(vertical = 4.dp)) {
                        listOf("FIFO", "POLLING", "RANDOM", "LATENCY").forEach { preset ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    val list = when(preset) {
                                        "FIFO" -> MockTemplates.FIFO_SEQUENCE
                                        "POLLING" -> MockTemplates.POLLING_LOADING
                                        "RANDOM" -> MockTemplates.RANDOM_ERRORS
                                        else -> MockTemplates.LONG_LATENCY
                                    }.map { DraftMockState(it.body, (it.durationMs?.div(1000) ?: 5).toString()) }
                                    
                                    viewModel.processIntent(MockIntent.ClearLogs)
                                    viewModel.processIntent(MockIntent.SetDraftMocks(list))
                                },
                                label = { Text(preset, fontSize = 10.sp) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.3f))
                    
                    Column(modifier = Modifier.weight(1f).verticalScroll(vScrollState)) {
                        Text("Route: /login & /api/custom", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        
                        state.draftMocks.forEachIndexed { index, draft ->
                            Card(
                                modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Response #${index + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        IconButton(onClick = { viewModel.processIntent(MockIntent.RemoveDraftMock(index)) }, modifier = Modifier.size(24.dp)) {
                                            Text("✕", fontSize = 12.sp, color = Color.Red)
                                        }
                                    }
                                    OutlinedTextField(
                                        value = draft.body,
                                        onValueChange = { viewModel.processIntent(MockIntent.UpdateDraftMock(index, it, draft.durationS)) },
                                        label = { Text("JSON Body", fontSize = 10.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                        maxLines = 4
                                    )
                                    OutlinedTextField(
                                        value = draft.durationS,
                                        onValueChange = { if (it.all { char -> char.isDigit() }) viewModel.processIntent(MockIntent.UpdateDraftMock(index, draft.body, it)) },
                                        label = { Text("Duration (seconds)", fontSize = 10.sp) },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                }
                            }
                        }
                        
                        TextButton(onClick = { viewModel.processIntent(MockIntent.AddDraftMock) }) {
                            Text("+ Add Response Item", fontSize = 12.sp)
                        }
                    }
                }
            }
        )
    }

    Row(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // LEFT: CONTROLS
        Column(
            modifier = Modifier.weight(0.35f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end = 8.dp)
        ) {
            Text("Controls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.processIntent(MockIntent.OpenEditor) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34A853)) // Google Green
            ) {
                Text("Edit Custom Mocks ✏️", color = Color.White, fontSize = 10.sp)
            }

            Text("Scenarios", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            
            val scenarioList = listOf("FIFO Sequence", "Polling / Refreshing", "Random Errors", "Long Latency", "Time-Based Transition")
            scenarioList.forEach { scenario ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().selectable(
                            selected = (state.selectedScenario == scenario),
                            onClick = { viewModel.processIntent(MockIntent.SelectScenario(scenario)) }
                        ).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (state.selectedScenario == scenario),
                            onClick = { viewModel.processIntent(MockIntent.SelectScenario(scenario)) }
                        )
                        Text(scenario, style = MaterialTheme.typography.bodySmall)
                    }
                    
                    if (state.selectedScenario == scenario) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.padding(start = 32.dp, end = 8.dp, bottom = 4.dp)
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
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(6.dp),
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            Button(
                onClick = { viewModel.processIntent(MockIntent.TogglePolling) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isPolling) Color(0xFFEA4335) else Color(0xFF4285F4) // Google Red / Blue
                )
            ) {
                val nextIcon = if (state.isPolling) "⏹" else "▶"
                Text("$nextIcon ${if (state.isPolling) "STOP" else "START (2s)"}", fontSize = 10.sp)
            }

            Spacer(Modifier.height(16.dp))
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))
            
            if (!state.isPolling) {
                Button(
                    onClick = { viewModel.processIntent(MockIntent.PerformSingleRequest) },
                    enabled = !state.isLoading,
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("SINGLE REQ", fontSize = 10.sp)
                    }
                }
            }
        }

        // RIGHT: TERMINAL LOGS
        Column(
            modifier = Modifier.weight(0.65f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Terminal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (state.logs.isNotEmpty()) {
                    Row {
                        TextButton(
                            onClick = { viewModel.processIntent(MockIntent.ClearLogs) },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Clear", fontSize = 12.sp, color = Color.Gray)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                val fullLog = state.logs.joinToString("\n") { "[${it.timestamp}] (+${it.elapsedSeconds}s) ${it.message}" }
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Mock Logs", fullLog)
                                clipboard.setPrimaryClip(clip)
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Copy", fontSize = 12.sp)
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.medium),
                shape = MaterialTheme.shapes.medium,
                color = Color.Black.copy(alpha = 0.9f)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    state = logScrollState
                ) {
                    items(state.logs) { log ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                                .background(
                                    if (log.isTransition) 
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                    else Color.Transparent,
                                    MaterialTheme.shapes.extraSmall
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = buildString {
                                    if (log.reqIndex > 0) append("#${log.reqIndex} ")
                                    append("[${log.timestamp} | +${log.elapsedSeconds}s] ")
                                    append(log.message)
                                },
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = if (log.isTransition) Color.White else Color.LightGray
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
