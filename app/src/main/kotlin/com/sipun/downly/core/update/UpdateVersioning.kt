package com.sipun.downly.core.update

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object UpdateVersioning {
    fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = versionParts(current)
        val latestParts = versionParts(latest)
        val count = maxOf(currentParts.size, latestParts.size)
        for (index in 0 until count) {
            val currentPart = currentParts.getOrElse(index) { 0 }
            val latestPart = latestParts.getOrElse(index) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }

    private fun versionParts(value: String): List<Int> = value.removePrefix("v").split(".")
        .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    fun formatReleaseDate(value: String): String? = runCatching {
        Instant.parse(value)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
    }.getOrNull()
}

fun String.toReleaseNotes(): String = lineSequence()
    .map { it.trim() }
    .filter { it.startsWith("-") }
    .map { it.removePrefix("-").trim().replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1") }
    .joinToString("\n")
