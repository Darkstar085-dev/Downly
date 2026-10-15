package com.sipun.downly.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sipun.downly.data.AudioOption
import com.sipun.downly.data.QualityOption
import com.sipun.youtubedl_android.mapper.VideoFormat
import java.util.Locale

private val COMMON_VIDEO_RESOLUTIONS = listOf(4320, 2160, 1440, 1080, 720, 480, 360, 240)
private val COMMON_AUDIO_BITRATES = listOf(320, 256, 192, 160, 128, 96, 64, 48, 32)

fun commonVideoResolution(width: Int, height: Int): Int? {
    if (height <= 0) return null
    val effectiveResolution = minOf(width.takeIf { it > 0 } ?: height, height)
    return COMMON_VIDEO_RESOLUTIONS.firstOrNull { effectiveResolution >= it }
}

fun commonAudioBitrate(bitrate: Int): Int? {
    if (bitrate <= 0) return null
    return COMMON_AUDIO_BITRATES.minByOrNull { kotlin.math.abs(it - bitrate) }
}

fun buildQualityOptions(formats: List<VideoFormat>): List<QualityOption> {
    val videoFormats = formats.filter { it.height > 0 && it.vcodec?.lowercase() != "none" }
    val audioFormats = formats.filter { it.vcodec?.lowercase() == "none" && it.acodec?.lowercase() != "none" }
    val bestAudio = audioFormats.maxWithOrNull(
        compareBy<VideoFormat> { it.abr.takeIf { bitrate -> bitrate > 0.0 } ?: it.tbr }
            .thenBy { it.fileSize.takeIf { size -> size > 0L } ?: it.fileSizeApproximate }
    )
    val audioBytes = bestAudio?.let {
        it.fileSize.takeIf { size -> size > 0L } ?: it.fileSizeApproximate.takeIf { size -> size > 0L }
    } ?: 0L

    return videoFormats.mapNotNull { format ->
        val resolution = commonVideoResolution(format.width, format.height) ?: return@mapNotNull null
        val formatId = format.formatId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val videoBytes = format.fileSize.takeIf { it > 0L } ?: format.fileSizeApproximate.takeIf { it > 0L } ?: 0L
        val selector = if (format.acodec?.lowercase() == "none") "$formatId+bestaudio/$formatId" else formatId
        Triple(format, resolution, QualityOption(
            quality = "${resolution}p",
            height = resolution,
            estimatedBytes = (videoBytes + audioBytes).takeIf { it > 0L },
            formatSelector = selector
        ))
    }.groupBy { it.second }.mapNotNull { (_, candidates) ->
        candidates.maxWithOrNull(
            compareBy<Triple<VideoFormat, Int, QualityOption>> { minOf(it.first.width.takeIf { width -> width > 0 } ?: it.first.height, it.first.height) }
                .thenBy { it.first.tbr }
                .thenBy { it.first.fileSize.takeIf { size -> size > 0L } ?: it.first.fileSizeApproximate }
        )?.third
    }.sortedByDescending { it.height }
}

fun buildAudioOptions(formats: List<VideoFormat>): List<AudioOption> {
    val candidates = formats
        .filter { it.vcodec?.lowercase() == "none" && it.acodec?.lowercase() != "none" }
        .mapNotNull { format ->
            val formatId = format.formatId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val bitrate = (format.abr.takeIf { it > 0.0 } ?: format.tbr).toInt()
            val commonBitrate = commonAudioBitrate(bitrate) ?: return@mapNotNull null
            val bytes = format.fileSize.takeIf { it > 0L } ?: format.fileSizeApproximate.takeIf { it > 0L }
            val extension = format.ext?.uppercase() ?: "SOURCE"
            AudioOption(
                formatId = formatId,
                quality = "$commonBitrate kbps · $extension",
                bitrate = commonBitrate,
                estimatedBytes = bytes
            ) to bitrate
        }

    return candidates.groupBy { it.first.bitrate }.mapNotNull { (_, options) ->
        options.maxWithOrNull(
            compareBy<Pair<AudioOption, Int>> { it.second }
                .thenBy { it.first.estimatedBytes ?: 0L }
                .thenBy { if (it.first.quality.endsWith("M4A")) 1 else 0 }
        )?.first
    }.sortedByDescending { it.bitrate }
}

fun recommendAudioOption(options: List<AudioOption>): AudioOption? =
    options.maxWithOrNull(compareBy<AudioOption> { it.bitrate }.thenBy { it.estimatedBytes ?: 0L })

fun defaultQualityFor(connectionType: String): String = when (connectionType) {
    "Wi-Fi", "Ethernet" -> "1080p"
    "Mobile data" -> "480p"
    else -> "360p"
}

fun recommendQuality(options: List<QualityOption>, connectionType: String): String {
    if (options.isEmpty()) return defaultQualityFor(connectionType)
    return options.maxByOrNull { it.height }?.quality ?: defaultQualityFor(connectionType)
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
