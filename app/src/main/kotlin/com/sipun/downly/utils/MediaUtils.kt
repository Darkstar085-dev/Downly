package com.sipun.downly.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.sipun.downly.data.AudioOption
import com.sipun.downly.data.QualityOption
import com.sipun.youtubedl_android.mapper.VideoFormat
import java.util.Locale

val DownlyGreen = Color(0xFF119B70)

fun buildQualityOptions(formats: List<VideoFormat>): List<QualityOption> {
    val videoFormats = formats.filter { it.height > 0 && it.vcodec?.lowercase() != "none" }
    val audioFormats = formats.filter { it.vcodec?.lowercase() == "none" && it.acodec?.lowercase() != "none" }
    return videoFormats.map { it.height }.distinct().sortedDescending().map { height ->
        val video = videoFormats.filter { it.height == height }.maxByOrNull { it.fileSize.takeIf { size -> size > 0 } ?: it.fileSizeApproximate }
        val audio = audioFormats.maxByOrNull { it.fileSize.takeIf { size -> size > 0 } ?: it.fileSizeApproximate }
        val videoBytes = video?.let { it.fileSize.takeIf { size -> size > 0 } ?: it.fileSizeApproximate } ?: 0L
        val audioBytes = audio?.let { it.fileSize.takeIf { size -> size > 0 } ?: it.fileSizeApproximate } ?: 0L
        QualityOption(height.toString() + "p", height, (videoBytes + audioBytes).takeIf { it > 0L })
    }
}

fun buildAudioOptions(formats: List<VideoFormat>): List<AudioOption> {
    return formats
        .filter { it.vcodec?.lowercase() == "none" && it.acodec?.lowercase() != "none" }
        .mapNotNull { format ->
            val formatId = format.formatId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val bitrate = (format.abr.takeIf { it > 0.0 } ?: format.tbr).toInt()
            val bytes = format.fileSize.takeIf { it > 0L } ?: format.fileSizeApproximate.takeIf { it > 0L }
            AudioOption(
                formatId = formatId,
                quality = (if (bitrate > 0) "$bitrate kbps" else "Audio") + " · " + (format.ext?.uppercase() ?: "SOURCE"),
                bitrate = bitrate,
                estimatedBytes = bytes
            )
        }
        .sortedWith(compareByDescending<AudioOption> { it.bitrate }.thenByDescending { it.estimatedBytes ?: 0L })
        .distinctBy { "${it.bitrate}:${it.quality.substringAfter(" · ")}" }
}

fun defaultQualityFor(connectionType: String): String = when (connectionType) {
    "Wi-Fi", "Ethernet" -> "1080p"
    "Mobile data" -> "480p"
    else -> "360p"
}

fun recommendQuality(options: List<QualityOption>, connectionType: String): String {
    if (options.isEmpty()) return defaultQualityFor(connectionType)
    val target = when (connectionType) {
        "Wi-Fi", "Ethernet" -> 1080
        "Mobile data" -> 480
        else -> 360
    }
    return options.filter { it.height <= target }.maxByOrNull { it.height }?.quality
        ?: options.minByOrNull { it.height }?.quality
        ?: defaultQualityFor(connectionType)
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
    else -> bytes.toString() + " B"
}

fun appContextOpen(intent: Intent) {
    runCatching {
        val context = DownlyContextRef.context ?: return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

object DownlyContextRef {
    var context: Context? = null
}

fun isValidUrl(value: String): Boolean {
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
    return uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
}

fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remaining)
    else String.format(Locale.US, "%d:%02d", minutes, remaining)
}

fun friendlyError(error: Throwable): String {
    val message = error.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
    return when {
        message.isBlank() -> "Something went wrong. Check your connection and try again."
        message.contains("unsupported url", true) -> "This link is not supported by yt-dlp."
        message.contains("private", true) || message.contains("sign in", true) -> "This media may require sign-in or may be private."
        message.contains("network", true) || message.contains("resolve host", true) || message.contains("connection", true) -> "Network problem. Check your connection and retry."
        else -> message.take(220)
    }
}

fun encodeText(value: String): String =
    android.util.Base64.encodeToString(value.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)

fun decodeText(value: String): String =
    String(android.util.Base64.decode(value, android.util.Base64.DEFAULT), Charsets.UTF_8)
