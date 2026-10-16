package com.sipun.downly.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sipun.downly.data.AudioOption
import com.sipun.downly.data.MediaOptions
import com.sipun.downly.data.QualityOption
import com.sipun.youtubedl_android.mapper.VideoFormat
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

fun estimateBytesFromBitrate(bitrateKbps: Double, durationSeconds: Int): Long? {
    if (bitrateKbps <= 0.0 || durationSeconds <= 0) return null
    return (bitrateKbps * 1000.0 / 8.0 * durationSeconds)
        .takeIf { it.isFinite() && it > 0.0 && it <= Long.MAX_VALUE.toDouble() }
        ?.toLong()
}

private fun fallbackVideoBitrateKbps(height: Int): Double = when {
    height >= 4320 -> 40000.0
    height >= 2160 -> 16000.0
    height >= 1440 -> 10000.0
    height >= 1080 -> 5000.0
    height >= 720 -> 2500.0
    height >= 480 -> 1200.0
    height >= 360 -> 700.0
    else -> 400.0
}

internal data class ResolvedFormatSize(val bytes: Long?, val approximate: Boolean)

internal fun resolveSizeValue(
    fileSize: Long,
    remoteSize: Long?,
    approximateSize: Long,
    bitrateKbps: Double,
    durationSeconds: Int
): ResolvedFormatSize {
    if (fileSize > 0L) return ResolvedFormatSize(fileSize, false)
    if (remoteSize != null && remoteSize > 0L) return ResolvedFormatSize(remoteSize, false)
    if (approximateSize > 0L) return ResolvedFormatSize(approximateSize, true)
    return ResolvedFormatSize(estimateBytesFromBitrate(bitrateKbps, durationSeconds), true)
}

private fun probeRemoteFileSize(format: VideoFormat): Long? {
    val rawUrl = format.url?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return null
    val urlPath = runCatching { URL(rawUrl).path.lowercase() }.getOrNull().orEmpty()
    if (format.manifestUrl != null || format.ext?.lowercase() in setOf("m3u8", "mpd") ||
        urlPath.endsWith(".m3u8") || urlPath.endsWith(".mpd")) return null

    fun connection(method: String): HttpURLConnection? = runCatching {
        (URL(rawUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = true
            connectTimeout = 700
            readTimeout = 700
            format.httpHeaders.orEmpty().forEach { (name, value) -> setRequestProperty(name, value) }
            if (method == "GET") setRequestProperty("Range", "bytes=0-0")
        }
    }.getOrNull()

    fun readLength(method: String): Long? {
        val response = connection(method) ?: return null
        return try {
            if (method == "HEAD") {
                if (response.responseCode in 200..299) {
                    response.getHeaderFieldLong("Content-Length", -1L).takeIf { it > 0L }
                } else null
            } else {
                val code = response.responseCode
                val rangeTotal = response.getHeaderField("Content-Range")
                    ?.substringAfterLast("/", "")
                    ?.toLongOrNull()
                when {
                    rangeTotal != null && rangeTotal > 0L -> rangeTotal
                    code == HttpURLConnection.HTTP_OK ->
                        response.getHeaderFieldLong("Content-Length", -1L).takeIf { it > 0L }
                    else -> null
                }
            }
        } finally {
            response.disconnect()
        }
    }

    return readLength("HEAD") ?: readLength("GET")
}

private fun resolveFormatSizes(formats: List<VideoFormat>, durationSeconds: Int): Map<String, ResolvedFormatSize> {
    val identified = formats.mapNotNull { format ->
        format.formatId?.takeIf { it.isNotBlank() }?.let { it to format }
    }.distinctBy { it.first }
    val results = identified.associate { (id, format) ->
        id to resolveSizeValue(
            fileSize = format.fileSize,
            remoteSize = null,
            approximateSize = format.fileSizeApproximate,
            bitrateKbps = (format.abr.takeIf { it > 0 } ?: format.tbr).toDouble(),
            durationSeconds = durationSeconds
        )
    }.toMutableMap()
    val needsProbe = identified.filter { (id, format) ->
        results[id]?.approximate == true && format.fileSize <= 0L &&
            format.url?.startsWith("http") == true
    }
    if (needsProbe.isNotEmpty()) {
        val executor = Executors.newFixedThreadPool(minOf(4, needsProbe.size))
        try {
            val pending = needsProbe.map { (id, format) -> id to executor.submit<Long?> { probeRemoteFileSize(format) } }
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1800)
            pending.forEach { (id, future) ->
                val remaining = deadline - System.nanoTime()
                val remoteSize = if (remaining > 0L) runCatching {
                    future.get(remaining, TimeUnit.NANOSECONDS)
                }.getOrNull() else null
                if (remoteSize != null && remoteSize > 0L) {
                    val format = identified.first { it.first == id }.second
                    results[id] = resolveSizeValue(
                        fileSize = format.fileSize,
                        remoteSize = remoteSize,
                        approximateSize = format.fileSizeApproximate,
                        bitrateKbps = (format.abr.takeIf { it > 0 } ?: format.tbr).toDouble(),
                        durationSeconds = durationSeconds
                    )
                } else {
                    future.cancel(true)
                }
            }
        } finally {
            executor.shutdownNow()
        }
    }
    identified.forEach { (id, format) ->
        val current = results[id]
        if (current == null || current.bytes == null) {
            results[id] = resolveSizeValue(
                fileSize = format.fileSize,
                remoteSize = null,
                approximateSize = format.fileSizeApproximate,
                bitrateKbps = (format.abr.takeIf { it > 0 } ?: format.tbr).toDouble(),
                durationSeconds = durationSeconds
            )
        }
    }
    return results
}

fun buildMediaOptions(formats: List<VideoFormat>, durationSeconds: Int = 0): MediaOptions {
    val sizes = resolveFormatSizes(formats, durationSeconds)
    val videoFormats = formats.filter { it.height > 0 && it.vcodec?.lowercase() != "none" }
    val audioFormats = formats.filter { it.vcodec?.lowercase() == "none" && it.acodec?.lowercase() != "none" }
    val bestAudio = audioFormats.maxWithOrNull(
        compareBy<VideoFormat> { it.abr.takeIf { bitrate -> bitrate > 0 } ?: it.tbr }
            .thenBy { it.fileSize.takeIf { size -> size > 0L } ?: it.fileSizeApproximate }
    )
    val bestAudioSize = bestAudio?.formatId?.let { sizes[it] }
    val bestAudioBytes = bestAudioSize?.bytes ?: 0L

    val qualityOptions = videoFormats.mapNotNull { format ->
        val resolution = commonVideoResolution(format.width, format.height) ?: return@mapNotNull null
        val formatId = format.formatId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val isVideoOnly = format.acodec?.lowercase() == "none"
        val formatSize = sizes[formatId]
        val videoBytes = formatSize?.bytes ?: 0L
        val totalBytes = if (isVideoOnly && bestAudioBytes > 0L) videoBytes + bestAudioBytes else videoBytes
        val approximate = isVideoOnly || formatSize?.approximate != false
        val selector = if (isVideoOnly) "$formatId+bestaudio/$formatId" else formatId
        Triple(format, resolution, QualityOption(
            quality = "${resolution}p",
            height = resolution,
            estimatedBytes = totalBytes.takeIf { it > 0L },
            formatSelector = selector,
            sizeIsApproximate = approximate
        ))
    }.groupBy { it.second }.mapNotNull { (_, candidates) ->
        candidates.maxWithOrNull(
            compareBy<Triple<VideoFormat, Int, QualityOption>> { minOf(it.first.width.takeIf { width -> width > 0 } ?: it.first.height, it.first.height) }
                .thenBy { it.first.tbr }
                .thenBy { it.first.fileSize.takeIf { size -> size > 0L } ?: it.first.fileSizeApproximate }
        )?.third
    }.sortedByDescending { it.height }

    val audioOptions = audioFormats.mapNotNull { format ->
        val formatId = format.formatId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val bitrate = (format.abr.takeIf { it > 0 } ?: format.tbr)
        val commonBitrate = commonAudioBitrate(bitrate) ?: return@mapNotNull null
        val size = sizes[formatId]
        val extension = format.ext?.uppercase() ?: "SOURCE"
        AudioOption(
            formatId = formatId,
            quality = "$commonBitrate kbps · $extension",
            bitrate = commonBitrate,
            estimatedBytes = size?.bytes,
            sizeIsApproximate = size?.approximate != false
        ) to bitrate
    }.groupBy { it.first.bitrate }.mapNotNull { (_, options) ->
        options.maxWithOrNull(
            compareBy<Pair<AudioOption, Int>> { it.second }
                .thenBy { it.first.estimatedBytes ?: 0L }
                .thenBy { if (it.first.quality.endsWith("M4A")) 1 else 0 }
        )?.first
    }.sortedByDescending { it.bitrate }

    return MediaOptions(qualityOptions, audioOptions)
}

fun buildQualityOptions(formats: List<VideoFormat>, durationSeconds: Int = 0): List<QualityOption> =
    buildMediaOptions(formats, durationSeconds).qualityOptions

fun buildAudioOptions(formats: List<VideoFormat>, durationSeconds: Int = 0): List<AudioOption> =
    buildMediaOptions(formats, durationSeconds).audioOptions

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
