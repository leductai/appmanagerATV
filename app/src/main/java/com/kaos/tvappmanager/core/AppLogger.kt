package com.kaos.tvappmanager.core

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

class AppLogger {
    private val entries = ArrayDeque<String>(MAX_ENTRIES)

    @Synchronized
    fun log(tag: String, message: String, throwable: Throwable? = null) {
        val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
        val body = buildString {
            append(stamp).append("  ").append(tag).append(": ").append(message)
            if (throwable != null) {
                append("  |  ").append(throwable.javaClass.simpleName)
                throwable.message?.let { append(": ").append(it) }
            }
        }
        entries.addLast(body)
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
    }

    @Synchronized
    fun dump(): List<String> = entries.toList()

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun asText(): String = entries.joinToString("\n")

    private companion object {
        const val MAX_ENTRIES = 500
    }
}
