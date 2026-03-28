package com.fusion.mock

import java.util.concurrent.ConcurrentHashMap
import java.time.LocalTime
import java.time.format.DateTimeFormatter

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun <K, V> createConcurrentMap(): MutableMap<K, V> = ConcurrentHashMap<K, V>()

actual fun formatTime(millis: Long): String {
    return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
}
