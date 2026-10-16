package com.sipun.downly.presentation

import android.content.ContentValues
import android.content.ContentUris
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import com.sipun.downly.runtime.RuntimeBundleInstaller
import com.sipun.downly.data.DownloadRecord
import com.sipun.downly.data.DownloadStatus
import com.sipun.downly.data.DuplicateDownloadPrompt
import com.sipun.downly.data.MediaMode
import com.sipun.downly.data.MediaPreview
import com.sipun.downly.data.ScreenState
import com.sipun.downly.utils.buildMediaOptions
import com.sipun.downly.utils.decodeText
import com.sipun.downly.utils.defaultQualityFor
import com.sipun.downly.utils.encodeText
import com.sipun.downly.utils.formatDuration
import com.sipun.downly.utils.friendlyError
import com.sipun.downly.utils.isValidUrl
import com.sipun.downly.utils.recommendQuality
import com.sipun.downly.utils.recommendAudioOption
import com.sipun.youtubedl_android.YoutubeDL
import com.sipun.youtubedl_android.YoutubeDLRequest
import com.sipun.youtubedl_android.mapper.VideoInfo
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private object RuntimeLibraryDirectoryConfiguration {
    private var ffmpegDirectory: String? = null
    private var aria2cDirectory: String? = null
    private var youtubeDlDirectory: String? = null

    @Synchronized
    fun configure(directory: File) {
        val path = directory.canonicalPath

        check(ffmpegDirectory == null || ffmpegDirectory == path) {
            "Runtime libraries changed during this process. Restart Downly to apply the update."
        }
        check(aria2cDirectory == null || aria2cDirectory == path) {
            "Runtime libraries changed during this process. Restart Downly to apply the update."
        }
        check(youtubeDlDirectory == null || youtubeDlDirectory == path) {
            "Runtime libraries changed during this process. Restart Downly to apply the update."
        }

        if (ffmpegDirectory == null) {
            com.sipun.ffmpeg.FFmpeg.setRuntimeArchiveDirectory(directory)
            ffmpegDirectory = path
        }
        if (aria2cDirectory == null) {
            com.sipun.aria2c.Aria2c.setRuntimeArchiveDirectory(directory)
            aria2cDirectory = path
        }
        if (youtubeDlDirectory == null) {
            YoutubeDL.setRuntimeArchiveDirectory(directory)
            youtubeDlDirectory = path
        }
    }
}

class DownlyViewModel : ViewModel() {
    private val worker = Executors.newSingleThreadExecutor()
    private val cancelledIds = ConcurrentHashMap.newKeySet<String>()
    private val queuedTaskGenerations = ConcurrentHashMap<String, Long>()
    private val nextTaskGeneration = AtomicLong()
    private val pausedIds = ConcurrentHashMap.newKeySet<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val persistenceWorker = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow(ScreenState())
    val state: StateFlow<ScreenState> = _state.asStateFlow()
    private var appContext: Context? = null
    private val initializationLock = Any()
    @Volatile private var initializationInFlight = false
    @Volatile private var initializationComplete = false
    @Volatile private var historyLoaded = false
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun attach(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            val settings = appContext!!.getSharedPreferences("downly-settings", Context.MODE_PRIVATE)
            val savedTheme = settings.getString("theme_mode", "System") ?: "System"
            val dynamicColor = settings.getBoolean("dynamic_color", false)
            val restoredHistory = readHistory(appContext!!)
            _state.value = _state.value.copy(
                themeMode = savedTheme,
                dynamicColor = dynamicColor,
                history = restoredHistory
            )
            historyLoaded = true
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
            ?.edit()
            ?.putString("theme_mode", value)
            ?.putBoolean("dynamic_color", _state.value.dynamicColor)
            ?.apply()
    }

    fun setDynamicColor(value: Boolean) {
        mutate { copy(dynamicColor = value) }
        appContext?.getSharedPreferences("downly-settings", Context.MODE_PRIVATE)
            ?.edit()?.putBoolean("dynamic_color", value)?.apply()
    }

    fun initialize() {
        synchronized(initializationLock) {
            if (initializationComplete || initializationInFlight || _state.value.initialized) return
            initializationInFlight = true
        }
        mutate { copy(busy = true, libraryProgress = null, message = "Preparing Libraries…") }
        worker.execute {
            val context = appContext
            if (context == null) {
                synchronized(initializationLock) { initializationInFlight = false }
                return@execute
            }
            try {
                mutate { copy(message = "Downloading Libraries archive…", libraryProgress = null) }
                val runtimeDir = RuntimeBundleInstaller.ensureInstalled(context) { progress ->
                    mutate { copy(libraryProgress = progress) }
                }
                RuntimeLibraryDirectoryConfiguration.configure(runtimeDir)
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
                synchronized(initializationLock) {
                    initializationComplete = true
                    initializationInFlight = false
                }
                mutate { copy(initialized = true, busy = false, libraryProgress = 1f, message = "Downloader ready") }
            } catch (error: Exception) {
                synchronized(initializationLock) {
                    initializationInFlight = false
                    initializationComplete = false
                }
                mutate { copy(initialized = false, busy = false, libraryProgress = null, message = friendlyError(error)) }
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
                val mediaOptions = buildMediaOptions(info.formats.orEmpty(), info.duration)
                val preview = MediaPreview(
                    title = info.title?.takeIf { it.isNotBlank() } ?: url,
                    uploader = info.uploader.orEmpty(),
                    duration = formatDuration(info.duration),
                    url = info.webpageUrl ?: url,
                    thumbnailUrl = info.thumbnail?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
                    durationSeconds = info.duration,
                    qualityOptions = mediaOptions.qualityOptions,
                    audioOptions = mediaOptions.audioOptions
                )
                mutate { copy(busy = false, preview = preview, recommendedQuality = recommendQuality(preview.qualityOptions, connectionType), message = "Media details ready") }
            } catch (error: Exception) {
                mutate { copy(busy = false, message = friendlyError(error)) }
            }
        }
    }

    fun startBestDownload() {
        val current = _state.value
        val selectedQuality = when (current.mode) {
            MediaMode.VIDEO -> current.preview?.qualityOptions
                ?.maxByOrNull { it.height }
                ?.let { "video:${it.formatSelector}" }
                ?: current.recommendedQuality
            MediaMode.AUDIO -> current.preview?.audioOptions
                ?.let { recommendAudioOption(it) }
                ?.let { "audio:${it.formatId}" }
        }
        startDownload(selectedQuality)
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
        val selectedQuality = qualityOverride ?: if (current.mode == MediaMode.VIDEO && current.quality == "Smart") current.recommendedQuality else current.quality
        val record = DownloadRecord(
            url = url,
            title = current.preview?.title ?: Uri.parse(url).host.orEmpty(),
            status = DownloadStatus.QUEUED,
            mode = current.mode,
            quality = selectedQuality
        )
        mutate { copy(history = listOf(record) + history, tab = 1, message = "Added to download activity") }
        scheduleDownload(record, current.mode, selectedQuality, current.connectionType)
    }

    private fun scheduleDownload(record: DownloadRecord, mode: MediaMode, quality: String, connectionType: String) {
        val generation = nextTaskGeneration.incrementAndGet()
        queuedTaskGenerations[record.id] = generation
        worker.execute {
            if (!queuedTaskGenerations.remove(record.id, generation)) return@execute
            if (cancelledIds.remove(record.id)) return@execute
            executeDownload(record, mode, quality, connectionType)
        }
    }

    private fun executeDownload(record: DownloadRecord, mode: MediaMode, quality: String, connectionType: String) {
        if (cancelledIds.remove(record.id)) return
        try {
            setRecord(record.id) { copy(status = DownloadStatus.DOWNLOADING, detail = "Starting yt-dlp…") }
            val context = appContext ?: error("Application context is unavailable.")
            val root = File(context.getExternalFilesDir(null) ?: context.filesDir, "Downly")
            val folder = File(root, record.id)
            if (!folder.exists() && !folder.mkdirs()) error("Unable to create download folder.")
            val request = YoutubeDLRequest(record.url)
                .addOption("-o", File(folder, "%(title).140B - %(id)s.%(ext)s").absolutePath)
                .addOption("--newline")
                .addOption("--continue")
                .addOption("--no-playlist")
            if (mode == MediaMode.AUDIO) {
                val audioFormatId = quality.takeIf { it.startsWith("audio:") }?.removePrefix("audio:")
                if (!audioFormatId.isNullOrBlank()) request.addOption("-f", audioFormatId)
                request.addOption("-x")
                    .addOption("--audio-format", "mp3")
                    .addOption("--audio-quality", if (connectionType == "Mobile data") "5" else "0")
            } else {
                val explicitSelector = quality.takeIf { it.startsWith("video:") }?.removePrefix("video:")
                val maxHeight = quality.removeSuffix("p").toIntOrNull()
                val format = explicitSelector?.takeIf { it.isNotBlank() }
                    ?: if (maxHeight != null) "bv*[height<=$maxHeight]+ba/b[height<=$maxHeight]" else "bv*+ba/b"
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
            val saved = folder.listFiles()?.filter { file ->
                file.isFile && !file.name.endsWith(".part") &&
                    !file.name.endsWith(".ytdl") && !file.name.endsWith(".temp")
            }?.maxByOrNull { it.lastModified() }
                ?: throw IOException("yt-dlp finished, but no completed output file was found in its download directory.")
            val mediaId = extractMediaId(saved.name)
            val normalized = normalizeDownloadedFileName(saved, record.title)
            val outputFile = if (normalized != saved.name) {
                val renamed = File(saved.parentFile, normalized)
                if (saved.renameTo(renamed)) renamed else saved
            } else saved
            val duplicate = findExistingMedia(context, outputFile.name, mode, mediaId)
            if (duplicate != null) {
                mutate {
                    copy(
                        duplicatePrompt = DuplicateDownloadPrompt(
                            recordId = record.id,
                            sourcePath = outputFile.absolutePath,
                            existingUri = duplicate.first.toString(),
                            existingPath = duplicate.second,
                            mode = mode,
                            fileName = outputFile.name
                        ),
                        message = "This file is already downloaded. Choose whether to replace it."
                    )
                }
                if (mediaId != null) saveMediaIdMapping(context, mediaId, duplicate.first)
                setRecord(record.id) { copy(detail = "Waiting for duplicate choice…") }
                return
            }
            val publicPath = publishToMediaStore(context, outputFile, mode, mediaId)
            outputFile.delete()
            setRecord(record.id) {
                copy(status = DownloadStatus.COMPLETE, progress = 1f, detail = "Download complete",
                    filePath = publicPath)
            }
        } catch (error: Exception) {
            val latest = _state.value.history.firstOrNull { it.id == record.id }
            val status = when {
                pausedIds.remove(record.id) || latest?.status == DownloadStatus.PAUSED -> DownloadStatus.PAUSED
                error is YoutubeDL.CanceledException -> DownloadStatus.CANCELLED
                else -> DownloadStatus.FAILED
            }
            setRecord(record.id) {
                copy(status = status, detail = if (status == DownloadStatus.PAUSED) "Paused · tap Resume to continue" else friendlyError(error))
            }
        } finally {
            appContext?.let { persistHistory(it) }
        }
    }

    private fun normalizeDownloadedFileName(file: File, title: String): String {
        val cleanTitle = title
            .replace(Regex("#[\\p{L}\\p{N}_-]+"), " ")
            .replace('_', ' ')
            .replace(Regex("[\\p{Cntrl}<>:\"/\\\\|?*]"), " ")
            .replace(Regex("[^\\p{L}\\p{N} .()'-]"), " ")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^[\\s._-]+|[\\s._-]+$"), "")
            .take(100)
            .trimEnd()
            .ifBlank { "Downly download" }
            .let { value ->
                val letters = value.filter { it.isLetter() }
                if (letters.isNotEmpty() && letters.all { it.isUpperCase() }) {
                    value.lowercase().split(' ').joinToString(" ") { word ->
                        word.replaceFirstChar { char ->
                            if (char.isLowerCase()) char.titlecase() else char.toString()
                        }
                    }
                } else value
            }
        return cleanTitle + "." + file.extension.lowercase().ifBlank { "mp4" }
    }

    private fun findExistingMedia(context: Context, fileName: String, mode: MediaMode, mediaId: String?): Pair<Uri, String>? {
        val relativeDirectory = if (mode == MediaMode.AUDIO) {
            Environment.DIRECTORY_DOWNLOADS + "/Downly/Music/"
        } else {
            Environment.DIRECTORY_DOWNLOADS + "/Downly/Videos/"
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val knownUri = mediaId?.let { readMediaIdMapping(context, it) }
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        context.contentResolver.query(
            collection, projection, selection, arrayOf(relativeDirectory), null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                val matchesName = name.equals(fileName, ignoreCase = true)
                val existingMediaId = extractMediaId(name)
                val matchesLegacyId = mediaId != null && existingMediaId.equals(mediaId, ignoreCase = true)
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                val uri = ContentUris.withAppendedId(collection, id)
                val matchesStoredId = knownUri != null && knownUri == uri.toString()
                if (matchesName || matchesLegacyId || matchesStoredId) {
                    return uri to (relativeDirectory + name)
                }
            }
        }
        return null
    }

    private fun saveMediaIdMapping(context: Context, mediaId: String, uri: Uri) {
        context.getSharedPreferences("downly-media-ids", Context.MODE_PRIVATE)
            .edit().putString(mediaId, uri.toString()).apply()
    }

    private fun readMediaIdMapping(context: Context, mediaId: String): String? {
        return context.getSharedPreferences("downly-media-ids", Context.MODE_PRIVATE)
            .getString(mediaId, null)
    }

    private fun extractMediaId(fileName: String): String? {
        val baseName = fileName.substringBeforeLast('.', fileName)
            .replace(Regex("\\s+\\(\\d+\\)$"), "")
            .trim()
        val bracketedId = Regex("\\[([^]]+)]").find(baseName)?.groupValues?.getOrNull(1)
        if (!bracketedId.isNullOrBlank()) return bracketedId.trim()

        val separator = " - "
        if (!baseName.contains(separator)) return null
        return baseName.substringAfterLast(separator).trim()
            .replace(Regex("\\s+\\(\\d+\\)$"), "")
            .takeIf { it.isNotBlank() }
    }

    fun resolveDuplicate(replaceExisting: Boolean) {
        val prompt = _state.value.duplicatePrompt ?: return
        val context = appContext ?: return
        worker.execute {
            try {
                val source = File(prompt.sourcePath)
                if (replaceExisting) {
                    val publicPath = replaceMediaStoreItem(context, Uri.parse(prompt.existingUri), source, prompt.existingPath)
                    source.delete()
                    setRecord(prompt.recordId) {
                        copy(status = DownloadStatus.COMPLETE, progress = 1f,
                            detail = "Download complete · replaced existing file", filePath = publicPath)
                    }
                    mutate { copy(duplicatePrompt = null, message = "Existing file replaced") }
                } else {
                    source.delete()
                    setRecord(prompt.recordId) {
                        copy(status = DownloadStatus.COMPLETE, progress = 1f,
                            detail = "Already downloaded · kept existing file", filePath = prompt.existingPath)
                    }
                    mutate { copy(duplicatePrompt = null, message = "Kept the existing download") }
                }
            } catch (error: Exception) {
                mutate { copy(duplicatePrompt = null, message = friendlyError(error)) }
                setRecord(prompt.recordId) { copy(status = DownloadStatus.FAILED, detail = friendlyError(error)) }
            } finally {
                appContext?.let { persistHistory(it) }
            }
        }
    }

    private fun replaceMediaStoreItem(context: Context, existingUri: Uri, source: File, existingPath: String): String {
        val resolver = context.contentResolver
        val backup = File.createTempFile("downly-replace-", ".bak", context.cacheDir)
        try {
            replaceWithRollback(
                backup = {
                    resolver.openInputStream(existingUri)?.use { input ->
                        backup.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IOException("Could not back up the existing download.")
                },
                replace = {
                    resolver.openOutputStream(existingUri, "wt")?.use { output ->
                        source.inputStream().use { input -> input.copyTo(output) }
                    } ?: throw IOException("Could not write the replacement file.")
                },
                restore = {
                    resolver.openOutputStream(existingUri, "wt")?.use { output ->
                        backup.inputStream().use { input -> input.copyTo(output) }
                    } ?: throw IOException("Could not restore the existing download.")
                }
            )
            return existingPath
        } finally {
            backup.delete()
        }
    }


    private fun publishToMediaStore(context: Context, source: File, mode: MediaMode, mediaId: String?): String {
        val resolver = context.contentResolver
        val relativeDirectory = if (mode == MediaMode.AUDIO) {
            Environment.DIRECTORY_DOWNLOADS + "/Downly/Music/"
        } else {
            Environment.DIRECTORY_DOWNLOADS + "/Downly/Videos/"
        }
        val mimeType = if (mode == MediaMode.AUDIO) {
            "audio/mpeg"
        } else {
            when (source.extension.lowercase()) {
                "webm" -> "video/webm"
                "mkv" -> "video/x-matroska"
                "mov" -> "video/quicktime"
                else -> "video/mp4"
            }
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDirectory)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Could not create the file in $relativeDirectory")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("Could not write the downloaded file.")
            val publishedValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            if (resolver.update(uri, publishedValues, null, null) <= 0) {
                throw IOException("Could not publish the downloaded file.")
            }
            if (!mediaId.isNullOrBlank()) saveMediaIdMapping(context, mediaId, uri)
            return relativeDirectory + source.name
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    fun pause(record: DownloadRecord) {
        val current = _state.value.history.firstOrNull { it.id == record.id } ?: return
        if (current.status == DownloadStatus.QUEUED) {
            queuedTaskGenerations.remove(record.id)
            pausedIds.add(record.id)
            setRecord(record.id) { copy(status = DownloadStatus.PAUSED, detail = "Paused · tap Resume to continue") }
            return
        }
        if (current.status == DownloadStatus.DOWNLOADING) {
            pausedIds.add(record.id)
            setRecord(record.id) { copy(status = DownloadStatus.PAUSED, detail = "Pausing…") }
            if (!YoutubeDL.destroyProcessById(record.id)) {
                pausedIds.remove(record.id)
                setRecord(record.id) { copy(status = DownloadStatus.DOWNLOADING, detail = "Downloading…") }
            }
        }
    }

    fun resume(record: DownloadRecord) {
        val current = _state.value.history.firstOrNull { it.id == record.id } ?: return
        if (current.status != DownloadStatus.PAUSED) return
        pausedIds.remove(record.id)
        val mode = current.mode
        val quality = current.quality
        setRecord(record.id) { copy(status = DownloadStatus.QUEUED, detail = "Resuming download…") }
        scheduleDownload(current.copy(status = DownloadStatus.QUEUED), mode, quality, _state.value.connectionType)
    }

    fun retry(record: DownloadRecord) {
        val current = _state.value.history.firstOrNull { it.id == record.id } ?: return
        if (current.status != DownloadStatus.FAILED) return
        setRecord(record.id) { copy(status = DownloadStatus.QUEUED, progress = 0f, detail = "Retrying download…") }
        scheduleDownload(current.copy(status = DownloadStatus.QUEUED), current.mode, current.quality, _state.value.connectionType)
    }

    fun cancel(record: DownloadRecord) {
        val current = _state.value.history.firstOrNull { it.id == record.id } ?: return
        if (current.status == DownloadStatus.QUEUED) {
            queuedTaskGenerations.remove(record.id)
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

    private fun readHistory(context: Context): List<DownloadRecord> {
        val stored = context.getSharedPreferences("downly-history", Context.MODE_PRIVATE)
            .getString("records", null) ?: return emptyList()
        return stored.lineSequence().mapNotNull { line ->
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
                    timestamp = p[7].toLongOrNull() ?: System.currentTimeMillis(),
                    mode = p.getOrNull(8)?.let { runCatching { MediaMode.valueOf(it) }.getOrNull() } ?: MediaMode.VIDEO,
                    quality = p.getOrNull(9)?.let(::decodeText) ?: "Best"
                )
            }.getOrNull()
        }.toList()
    }

    private fun persistHistory(context: Context, snapshot: List<DownloadRecord> = _state.value.history) {
        if (!historyLoaded) return
        val encoded = snapshot.joinToString("\n") { item ->
            listOf(item.id, encodeText(item.url), encodeText(item.title), item.status.name,
                item.progress.toString(), encodeText(item.detail), item.filePath?.let(::encodeText) ?: "-",
                item.timestamp.toString(), item.mode.name, encodeText(item.quality)).joinToString("|")
        }
        persistenceWorker.execute {
            context.getSharedPreferences("downly-history", Context.MODE_PRIVATE).edit()
                .putString("records", encoded).commit()
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

