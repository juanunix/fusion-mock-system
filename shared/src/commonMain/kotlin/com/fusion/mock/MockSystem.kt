package com.fusion.mock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Global entry point for the Mock System to prevent multiple instantiations.
 * Provides a shared StatefulMockProvider instance.
 */
object MockSystem {
    val provider = StatefulMockProvider()
    
    // Shared scope for background tasks if needed
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    fun init() {
        println("Fusion Mock System Initialized (Singleton)")
    }
}
