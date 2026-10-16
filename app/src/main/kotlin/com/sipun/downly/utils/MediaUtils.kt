package com.sipun.downly.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sipun.downly.data.AudioOption
import com.sipun.downly.data.MediaOptions
import com.sipun.downly.data.MediaMode
import com.sipun.downly.data.QualityOption
import com.sipun.youtubedl_android.mapper.VideoFormat
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val COMMON_AUDIO_BITRATES = listOf(320, 256, 192, 160, 128, 96, 64, 48, 32)

fun commonVideoResolution(width: Int, height: Int): Int? {
    if (height <= 0) return null
    val effectiveResolution = minOf(width.takeIf { it > 0 } ?: height, height)
    return effectiveResolution.takeIf { it >= 240 }
}

private val GENERIC_VIDEO_QUALITIES = listOf(4320, 2160, 1440, 1080, 720, 480, 360, 240, 144)

internal fun genericVideoFormatSelector(maxHeight: Int): String =
    "bv*[height<=$maxHeight]+ba/b[height<=$maxHeight]/bv*+ba/b"

fun genericVideoQualityHeights(availableHeights: List<Int>): List<Int> {
    val usableHeights = availableHeights.filter { it >= 144 }
    val maximumHeight = usableHeights.maxOrNull() ?: return emptyList()
    val choices = GENERIC_VIDEO_QUALITIES.filter { it <= maximumHeight } +
        listOfNotNull(maximumHeight.takeIf { it !in GENERIC_VIDEO_QUALITIES })
    return choices.distinct()
        .filter { cap -> usableHeights.any { it <= cap } }
        .sortedDescending()
}

fun downloadQualityTag(mode: MediaMode, quality: String): String {
    val selector = when {
        quality.startsWith("video:") -> quality.removePrefix("video:")
        quality.startsWith("audio:") -> quality.removePrefix("audio:")
        else -> quality
    }
    val safeSelector = selector.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(48)
        .trim('-')
        .ifBlank { "default" }
    return "${mode.name.lowercase()}-$safeSelector"
}

fun mediaIdentityKey(mediaId: String, mode: MediaMode, quality: String): String =
    "$mediaId:${downloadQualityTag(mode, quality)}"

fun downloadFilenameQuality(mode: MediaMode, quality: String): String {
    if (mode == MediaMode.AUDIO) {
        val formatId = quality.removePrefix("audio:").takeIf { it.isNotBlank() } ?: "audio"
        return "audio-" + formatId.lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-').ifBlank { "audio" }
    }
    val height = Regex("""height\s*<=\s*(\d+)""").find(quality)?.groupValues?.get(1)
        ?: quality.removePrefix("video:").removeSuffix("p").toIntOrNull()?.toString()
    return height?.let { "${it}p" } ?: "video"
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

fun vcodecRank(vcodec: String?): Int {
    val codec = vcodec?.lowercase().orEmpty()
    return when {
        codec.startsWith("av01") || codec.startsWith("av1") -> 4
        codec.startsWith("vp09") || codec.startsWith("vp9") -> 3
        codec.startsWith("avc1") || codec.startsWith("h264") -> 2
        codec.isNotBlank() && codec != "none" -> 1
        else -> 0
    }
}

fun fpsRank(fps: Int): Int = fps.takeIf { it > 0 } ?: 0

private fun fallbackVideoBitrateKbps(height: Int): Double = when {
    height >= 4320 -> 25000.0
    height >= 2160 -> 8500.0
    height >= 1440 -> 4800.0
    height >= 1080 -> 2400.0
    height >= 720 -> 1200.0
    height >= 480 -> 600.0
    height >= 360 -> 350.0
    else -> 200.0
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

internal fun estimateFormatBytes(
    size: ResolvedFormatSize?,
    bitrateKbps: Double,
    durationSeconds: Int
): Long? {
    if (size?.bytes != null && size.bytes > 0L) return size.bytes
    return estimateBytesFromBitrate(bitrateKbps, durationSeconds)
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
        val bitrate = (format.abr.takeIf { it > 0 }
            ?: format.tbr.takeIf { it > 0 }
            ?: fallbackVideoBitrateKbps(format.height)).toDouble()
        id to resolveSizeValue(
            fileSize = format.fileSize,
            remoteSize = null,
            approximateSize = format.fileSizeApproximate,
            bitrateKbps = bitrate,
            durationSeconds = durationSeconds
        )
    }.toMutableMap()
    val needsProbe = identified.filter { (_, format) ->
        format.fileSize <= 0L && format.fileSizeApproximate <= 0L &&
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
                    val bitrate = (format.abr.takeIf { it > 0 }
                        ?: format.tbr.takeIf { it > 0 }
                        ?: fallbackVideoBitrateKbps(format.height)).toDouble()
                    results[id] = resolveSizeValue(
                        fileSize = format.fileSize,
                        remoteSize = remoteSize,
                        approximateSize = format.fileSizeApproximate,
                        bitrateKbps = bitrate,
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
            val bitrate = (format.abr.takeIf { it > 0 }
                ?: format.tbr.takeIf { it > 0 }
                ?: fallbackVideoBitrateKbps(format.height)).toDouble()
            results[id] = resolveSizeValue(
                fileSize = format.fileSize,
                remoteSize = null,
                approximateSize = format.fileSizeApproximate,
                bitrateKbps = bitrate,
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
    val bestAudioBitrate = bestAudio?.let { (it.abr.takeIf { bitrate -> bitrate > 0 } ?: it.tbr).toDouble() } ?: 0.0
    val bestAudioBytes = bestAudioSize?.bytes ?: 0L

    val videoCandidates = videoFormats.mapNotNull { format ->
        val formatId = format.formatId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val resolution = minOf(format.width.takeIf { it > 0 } ?: format.height, format.height)
            .takeIf { it >= 144 } ?: return@mapNotNull null
        Triple(format, resolution, sizes[formatId])
    }

    val qualityOptions = genericVideoQualityHeights(videoCandidates.map { it.second }).mapNotNull { maxHeight ->
        val selected = videoCandidates
            .filter { it.second <= maxHeight }
            .maxWithOrNull(
                compareBy<Triple<VideoFormat, Int, ResolvedFormatSize?>> { it.second }
                    .thenBy { vcodecRank(it.first.vcodec) }
                    .thenBy { fpsRank(it.first.fps) }
                    .thenBy { it.third?.bytes ?: 0L }
                    .thenBy { it.first.tbr }
            ) ?: return@mapNotNull null
        val format = selected.first
        val isVideoOnly = format.acodec?.lowercase() == "none"
        val videoBytes = estimateFormatBytes(selected.third, format.tbr.toDouble(), durationSeconds)
        val audioBytes = estimateFormatBytes(bestAudioSize, bestAudioBitrate, durationSeconds)
            ?: bestAudioBytes.takeIf { it > 0L }
        val totalBytes = if (isVideoOnly) {
            listOfNotNull(videoBytes, audioBytes).takeIf { it.isNotEmpty() }?.sum()
        } else videoBytes

        QualityOption(
            quality = "${maxHeight}p",
            height = maxHeight,
            estimatedBytes = totalBytes?.takeIf { it > 0L },
            formatSelector = genericVideoFormatSelector(maxHeight),
            sizeIsApproximate = selected.third?.approximate == true || (isVideoOnly && bestAudioSize?.approximate == true)
        )
    }

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
            estimatedBytes = size?.bytes?.takeIf { it > 0L } ?: estimateBytesFromBitrate(bitrate.toDouble(), durationSeconds),
            sizeIsApproximate = size?.approximate ?: true
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

fun downloadTotalSize(detail: String): String? {
    val match = Regex("""\bof\s+(\d+(?:\.\d+)?)\s*(B|KB|MB|GB|TB|KiB|MiB|GiB|TiB)\b""", RegexOption.IGNORE_CASE)
        .find(detail) ?: return null
    val amount = match.groupValues[1]
    val unit = match.groupValues[2]
    return "$amount $unit"
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> {
        val gib = bytes / 1_073_741_824.0
        if (gib >= 10.0) String.format(Locale.US, "%.1f GiB", gib)
        else String.format(Locale.US, "%.2f GiB", gib)
    }
    bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MiB", bytes / 1_048_576.0)
    bytes >= 1024L -> String.format(Locale.US, "%.0f KiB", bytes / 1024.0)
    else -> "$bytes B"
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
