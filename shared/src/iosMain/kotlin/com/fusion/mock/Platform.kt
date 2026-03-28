package com.fusion.mock

import kotlin.native.concurrent.AtomicLong
import platform.posix.gettimeofday
import kotlinx.cinterop.*

actual fun currentTimeMillis(): Long = memScoped {
    val tv = alloc<platform.posix.timeval>()
    gettimeofday(tv.ptr, null)
    (tv.tv_sec * 1000L) + (tv.tv_usec / 1000L)
}

actual fun <K, V> createConcurrentMap(): MutableMap<K, V> = mutableMapOf<K, V>()

actual fun formatTime(millis: Long): String = "T" // Placeholder for iOS
