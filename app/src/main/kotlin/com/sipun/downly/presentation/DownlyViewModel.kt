package com.sipun.downly.presentation

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import com.sipun.downly.runtime.RuntimeBundleInstaller
import com.sipun.downly.data.DownloadRecord
import com.sipun.downly.data.DownloadStatus
import com.sipun.downly.data.MediaMode
import com.sipun.downly.data.MediaPreview
import com.sipun.downly.data.ScreenState
import com.sipun.downly.utils.buildAudioOptions
import com.sipun.downly.utils.buildQualityOptions
import com.sipun.downly.utils.decodeText
import com.sipun.downly.utils.defaultQualityFor
import com.sipun.downly.utils.encodeText
import com.sipun.downly.utils.formatDuration
import com.sipun.downly.utils.friendlyError
import com.sipun.downly.utils.isValidUrl
import com.sipun.downly.utils.recommendQuality
import com.sipun.youtubedl_android.YoutubeDL
import com.sipun.youtubedl_android.YoutubeDLRequest
import com.sipun.youtubedl_android.mapper.VideoInfo
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DownlyViewModel : ViewModel() {
    private val worker = Executors.newSingleThreadExecutor()
    private val cancelledIds = ConcurrentHashMap.newKeySet<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val persistenceWorker = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow(ScreenState())
    val state: StateFlow<ScreenState> = _state.asStateFlow()
    private var appContext: Context? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun attach(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            val savedTheme = appContext!!.getSharedPreferences("downly-settings", Context.MODE_PRIVATE)
                .getString("theme_mode", "System") ?: "System"
            _state.value = _state.value.copy(themeMode = savedTheme)
            connectivityManager = appContext!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = updateConnectionType()
                override fun onLost(network: Network) = updateConnectionType()
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = updateConnectionType()
            }
            networkCallback = callback
            runCatching { connectivityManager?.registerDefaultNetworkCallback(callback) }
            updateConnectionType()
        }
        initialize()
    }

    fun setUrl(value: String) = mutate { copy(url = value, preview = null, message = "") }
    fun setMode(value: MediaMode) = mutate { copy(mode = value) }
    fun setQuality(value: String) = mutate { copy(quality = value) }
    fun setTab(value: Int) = mutate { copy(tab = value) }

    fun setThemeMode(value: String) {
        mutate { copy(themeMode = value) }
        appContext?.getSharedPreferences("downly-settings", Context.MODE_PRIVATE)
            ?.edit()?.putString("theme_mode", value)?.apply()
    }

    fun initialize() {
        if (_state.value.initialized || _state.value.busy) return
        mutate { copy(busy = true, message = "Preparing Libraries…") }
        worker.execute {
            val context = appContext ?: return@execute
            try {
                mutate { copy(message = "Downloading Libraries archive…") }
                val runtimeDir = RuntimeBundleInstaller.ensureInstalled(context)
                com.sipun.ffmpeg.FFmpeg.setRuntimeArchiveDirectory(runtimeDir)
                com.sipun.aria2c.Aria2c.setRuntimeArchiveDirectory(runtimeDir)
                YoutubeDL.setRuntimeArchiveDirectory(runtimeDir)
                val baseDir = File(context.noBackupFilesDir, "youtubedl-android")
                val packagesDir = File(baseDir, "packages")

                val pythonArchive = File(runtimeDir, "libpython.zip.so")
                if (pythonArchive.isFile) {
                    val pythonDir = File(packagesDir, "python")
                    val version = com.sipun.youtubedl_common.utils.RuntimeArchiveUtils.sha256(pythonArchive)
                    val root = File(pythonDir, "usr/lib")
                    val versions = root.listFiles()?.filter {
                        it.isDirectory && it.name.matches(Regex("python3\\.\\d+"))
                    }.orEmpty()
                    val isValid = versions.any { File(it, "encodings/__init__.py").isFile }
                    if (!isValid || version != com.sipun.youtubedl_common.SharedPrefsHelper[context, "pythonLibVersion"]) {
                        com.sipun.youtubedl_common.utils.RuntimeArchiveUtils.installArchive(pythonArchive, pythonDir) { staged ->
                            val rootStaged = File(staged, "usr/lib")
                            val versionsStaged = rootStaged.listFiles()?.filter {
                                it.isDirectory && it.name.matches(Regex("python3\\.\\d+"))
                            }.orEmpty()
                            if (!versionsStaged.any { File(it, "encodings/__init__.py").isFile }) {
                                throw IOException("Python runtime archive is missing its standard library")
                            }
                        }
                        com.sipun.youtubedl_common.SharedPrefsHelper.update(context, "pythonLibVersion", version)
                    }
                }

                val ffmpegArchive = File(runtimeDir, "libffmpeg.zip.so")
                if (ffmpegArchive.isFile) {
                    val ffmpegDir = File(packagesDir, "ffmpeg")
                    val version = com.sipun.youtubedl_common.utils.RuntimeArchiveUtils.sha256(ffmpegArchive)
                    val libraryDirectory = File(ffmpegDir, "usr/lib")
                    val needsInstall = !libraryDirectory.isDirectory ||
                        libraryDirectory.list()?.isEmpty() != false ||
                        version != com.sipun.youtubedl_common.SharedPrefsHelper[context, "ffmpegLibVersion"]
                    if (needsInstall) {
                        com.sipun.youtubedl_common.utils.RuntimeArchiveUtils.installArchive(ffmpegArchive, ffmpegDir) { staged ->
                            val stagedLibraries = File(staged, "usr/lib")
                            if (!stagedLibraries.isDirectory || stagedLibraries.list()?.isEmpty() != false) {
                                throw IOException("FFmpeg archive contains no runtime libraries")
                            }
                        }
                        com.sipun.youtubedl_common.SharedPrefsHelper.update(context, "ffmpegLibVersion", version)
                    }
                }

                val aria2cArchive = File(runtimeDir, "libaria2c.zip.so")
                if (aria2cArchive.isFile) {
                    val aria2cDir = File(packagesDir, "aria2c")
                    val version = com.sipun.youtubedl_common.utils.RuntimeArchiveUtils.sha256(aria2cArchive)
                    val libraryDirectory = File(aria2cDir, "usr/lib")
                    val needsInstall = !libraryDirectory.isDirectory ||
                        libraryDirectory.list()?.isEmpty() != false ||
                        version != com.sipun.youtubedl_common.SharedPrefsHelper[context, "aria2cLibVersion"]
                    if (needsInstall) {
                        com.sipun.youtubedl_common.utils.RuntimeArchiveUtils.installArchive(aria2cArchive, aria2cDir) { staged ->
                            val stagedLibraries = File(staged, "usr/lib")
                            if (!stagedLibraries.isDirectory || stagedLibraries.list()?.isEmpty() != false) {
                                throw IOException("aria2 archive contains no runtime libraries")
                            }
                        }
                        com.sipun.youtubedl_common.SharedPrefsHelper.update(context, "aria2cLibVersion", version)
                    }
                }

                com.sipun.ffmpeg.FFmpeg.init(context)
                runCatching { com.sipun.aria2c.Aria2c.init(context) }
                YoutubeDL.init(context)
                mutate { copy(initialized = true, busy = false, message = "Downloader ready") }
                loadHistory(context)
            } catch (error: Exception) {
                mutate { copy(initialized = false, busy = false, message = friendlyError(error)) }
            }
        }
    }

    fun fetchInfo() {
        val current = _state.value
        val url = current.url.trim()
        if (!isValidUrl(url)) {
            mutate { copy(message = "Paste a valid http/https URL first.") }
            return
        }
        if (!current.initialized) {
            initialize()
            mutate { copy(message = "Downloader is starting. Please tap Fetch info again shortly.") }
            return
        }
        mutate { copy(busy = true, message = "Fetching media information…", preview = null) }
        worker.execute {
            try {
                val info: VideoInfo = YoutubeDL.getInfo(url)
                val preview = MediaPreview(
                    title = info.title?.takeIf { it.isNotBlank() } ?: url,
                    uploader = info.uploader.orEmpty(),
                    duration = formatDuration(info.duration),
                    url = info.webpageUrl ?: url,
                    thumbnailUrl = info.thumbnail?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
                    qualityOptions = buildQualityOptions(info.formats.orEmpty()),
                    audioOptions = buildAudioOptions(info.formats.orEmpty())
                )
                mutate { copy(busy = false, preview = preview, recommendedQuality = recommendQuality(preview.qualityOptions, connectionType), message = "Media details ready") }
            } catch (error: Exception) {
                mutate { copy(busy = false, message = friendlyError(error)) }
            }
        }
    }

    fun startDownload(qualityOverride: String? = null) {
        val current = _state.value
        val url = current.url.trim()
        if (!isValidUrl(url)) {
            mutate { copy(message = "Paste a valid http/https URL first.") }
            return
        }
        if (!current.initialized) {
            initialize()
            mutate { copy(message = "Downloader is starting. Retry in a moment.") }
            return
        }
        if (current.history.any {
                it.url == url && it.status in setOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)
            }) {
            mutate { copy(message = "This link is already active.") }
            return
        }
        val record = DownloadRecord(
            url = url,
            title = current.preview?.title ?: Uri.parse(url).host.orEmpty(),
            status = DownloadStatus.QUEUED
        )
        mutate { copy(history = listOf(record) + history, tab = 1, message = "Added to download activity") }
        val selectedQuality = qualityOverride ?: if (current.mode == MediaMode.VIDEO && current.quality == "Smart") current.recommendedQuality else current.quality
        worker.execute {
            if (cancelledIds.remove(record.id)) return@execute
            executeDownload(record, current.mode, selectedQuality, current.connectionType)
        }
    }

    private fun executeDownload(record: DownloadRecord, mode: MediaMode, quality: String, connectionType: String) {
        if (cancelledIds.remove(record.id)) return
        try {
            setRecord(record.id) { copy(status = DownloadStatus.DOWNLOADING, detail = "Starting yt-dlp…") }
            val context = appContext ?: error("Application context is unavailable.")
            val folder = File(context.getExternalFilesDir(null), "Downly")
            if (!folder.exists() && !folder.mkdirs()) error("Unable to create download folder.")
            val request = YoutubeDLRequest(record.url)
                .addOption("-o", File(folder, "%(title).180B [%(id)s].%(ext)s").absolutePath)
                .addOption("--newline")
                .addOption("--no-playlist")
                .addOption("--restrict-filenames")
            if (mode == MediaMode.AUDIO) {
                val audioFormatId = quality.takeIf { it.startsWith("audio:") }?.removePrefix("audio:")
                if (!audioFormatId.isNullOrBlank()) request.addOption("-f", audioFormatId)
                request.addOption("-x")
                    .addOption("--audio-format", "mp3")
                    .addOption("--audio-quality", if (connectionType == "Mobile data") "5" else "0")
            } else {
                val maxHeight = quality.removeSuffix("p").toIntOrNull()
                val format = if (maxHeight != null) "bv*[height<=$maxHeight]+ba/b[height<=$maxHeight]" else "bv*+ba/b"
                request.addOption("-f", format)
                    .addOption("--merge-output-format", "mp4")
            }
            if (cancelledIds.remove(record.id)) return
            YoutubeDL.execute(request, record.id) { percent, eta, line ->
                val fraction = (percent / 100f).coerceIn(0f, 1f)
                setRecord(record.id) {
                    copy(status = DownloadStatus.DOWNLOADING, progress = fraction,
                        detail = line.take(150).ifBlank { if (eta > 0) "About ${eta}s left" else "Downloading…" })
                }
            }
            val saved = folder.listFiles()?.filter { it.isFile && it.lastModified() >= record.timestamp }?.maxByOrNull { it.lastModified() }
            setRecord(record.id) {
                copy(status = DownloadStatus.COMPLETE, progress = 1f, detail = "Download complete",
                    filePath = saved?.absolutePath)
            }
        } catch (error: Exception) {
            val status = if (error is YoutubeDL.CanceledException) DownloadStatus.CANCELLED else DownloadStatus.FAILED
            setRecord(record.id) { copy(status = status, detail = friendlyError(error)) }
        } finally {
            appContext?.let { persistHistory(it) }
        }
    }

    fun cancel(record: DownloadRecord) {
        val current = _state.value.history.firstOrNull { it.id == record.id } ?: return
        if (current.status == DownloadStatus.QUEUED) {
            cancelledIds.add(record.id)
            setRecord(record.id) { copy(status = DownloadStatus.CANCELLED, detail = "Cancelled before start") }
            return
        }
        if (current.status == DownloadStatus.DOWNLOADING && YoutubeDL.destroyProcessById(record.id)) {
            setRecord(record.id) { copy(status = DownloadStatus.CANCELLED, detail = "Cancelled") }
        }
    }

    fun delete(record: DownloadRecord) {
        mutate { copy(history = history.filterNot { it.id == record.id }) }
    }

    fun clearFinished() {
        mutate {
            copy(history = history.filterNot {
                it.status in setOf(DownloadStatus.COMPLETE, DownloadStatus.FAILED, DownloadStatus.CANCELLED)
            })
        }
    }

    private fun loadHistory(context: Context) {
        val stored = context.getSharedPreferences("downly-history", Context.MODE_PRIVATE)
            .getString("records", null) ?: return
        val records = stored.lineSequence().mapNotNull { line ->
            val p = line.split('|')
            if (p.size < 8) return@mapNotNull null
            runCatching {
                val status = DownloadStatus.valueOf(p[3]).let {
                    if (it == DownloadStatus.DOWNLOADING || it == DownloadStatus.QUEUED) DownloadStatus.FAILED else it
                }
                DownloadRecord(
                    id = p[0], url = decodeText(p[1]), title = decodeText(p[2]), status = status,
                    progress = p[4].toFloatOrNull() ?: 0f, detail = decodeText(p[5]),
                    filePath = p[6].takeIf { it != "-" }?.let(::decodeText),
                    timestamp = p[7].toLongOrNull() ?: System.currentTimeMillis()
                )
            }.getOrNull()
        }.toList()
        mutate { copy(history = records) }
    }

    private fun persistHistory(context: Context, snapshot: List<DownloadRecord> = _state.value.history) {
        val encoded = snapshot.joinToString("\n") { item ->
            listOf(item.id, encodeText(item.url), encodeText(item.title), item.status.name,
                item.progress.toString(), encodeText(item.detail), item.filePath?.let(::encodeText) ?: "-",
                item.timestamp.toString()).joinToString("|")
        }
        persistenceWorker.execute {
            context.getSharedPreferences("downly-history", Context.MODE_PRIVATE).edit()
                .putString("records", encoded).apply()
        }
    }

    private fun setRecord(id: String, change: DownloadRecord.() -> DownloadRecord) {
        mutate { copy(history = history.map { if (it.id == id) it.change() else it }) }
    }

    private fun mutate(change: ScreenState.() -> ScreenState) {
        mainHandler.post {
            val next = _state.value.change()
            _state.value = next
            appContext?.let { persistHistory(it, next.history) }
        }
    }

    private fun updateConnectionType() {
        val manager = connectivityManager ?: return
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        val type = when {
            capabilities == null -> "Offline"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
        mutate { copy(connectionType = type, recommendedQuality = preview?.let { recommendQuality(it.qualityOptions, type) } ?: defaultQualityFor(type)) }
    }

    override fun onCleared() {
        networkCallback?.let { callback -> runCatching { connectivityManager?.unregisterNetworkCallback(callback) } }
        worker.shutdownNow()
        persistenceWorker.shutdown()
    }
}
