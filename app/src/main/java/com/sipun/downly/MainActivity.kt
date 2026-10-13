package com.sipun.downly

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sipun.youtubedl_android.YoutubeDL
import com.sipun.youtubedl_android.YoutubeDLRequest
import com.sipun.youtubedl_android.mapper.VideoInfo
import com.sipun.youtubedl_android.mapper.VideoFormat
import coil.compose.AsyncImage
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val DownlyGreen = Color(0xFF119B70)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DownlyContextRef.context = applicationContext
        setContent { DownlyApp() }
    }
}

enum class MediaMode { VIDEO, AUDIO }
enum class DownloadStatus { QUEUED, DOWNLOADING, COMPLETE, FAILED, CANCELLED }

data class DownloadRecord(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String,
    val status: DownloadStatus,
    val progress: Float = 0f,
    val detail: String = "Queued",
    val filePath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class QualityOption(val quality: String, val height: Int, val estimatedBytes: Long?)
data class AudioOption(val formatId: String, val quality: String, val bitrate: Int, val estimatedBytes: Long?)

data class MediaPreview(
    val title: String,
    val uploader: String,
    val duration: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val qualityOptions: List<QualityOption> = emptyList(),
    val audioOptions: List<AudioOption> = emptyList()
)

data class ScreenState(
    val url: String = "",
    val initialized: Boolean = false,
    val busy: Boolean = false,
    val message: String = "Starting downloader…",
    val preview: MediaPreview? = null,
    val mode: MediaMode = MediaMode.VIDEO,
    val quality: String = "Smart",
    val connectionType: String = "Checking connection…",
    val recommendedQuality: String = "480p",
    val tab: Int = 0,
    val history: List<DownloadRecord> = emptyList(),
    val themeMode: String = "System"
)

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
        mutate { copy(busy = true, message = "Preparing downloader runtime…") }
        worker.execute {
            val context = appContext ?: return@execute
            try {
                com.sipun.ffmpeg.FFmpeg.init(context)
                // aria2c is optional; failure should not prevent the core downloader from starting.
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownlyApp(vm: DownlyViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.attach(context) }
    BackHandler(enabled = state.tab != 0) { vm.setTab(0) }

    val darkTheme = when (state.themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }
    val downlyColors = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF79D8B0), onPrimary = Color(0xFF003824),
            primaryContainer = Color(0xFF075C3D), onPrimaryContainer = Color(0xFFB8F5D8),
            secondary = Color(0xFFB3CCBF), onSecondary = Color(0xFF20372C),
            tertiary = Color(0xFFB9D8C9), background = Color(0xFF101512),
            surface = Color(0xFF171E19), surfaceContainerLow = Color(0xFF1D2721),
            surfaceContainer = Color(0xFF232D27), onSurface = Color(0xFFE7EEE9),
            onSurfaceVariant = Color(0xFFA8B4AC), secondaryContainer = Color(0xFF294438),
            onSecondaryContainer = Color(0xFFD5F4E4), outline = Color(0xFF607067),
            outlineVariant = Color(0xFF3A4940)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF087B56), onPrimary = Color.White,
            primaryContainer = Color(0xFFB9F0D6), onPrimaryContainer = Color(0xFF002113),
            secondary = Color(0xFF426657), onSecondary = Color.White,
            tertiary = Color(0xFF486458), background = Color(0xFFF6F8F6),
            surface = Color(0xFFFCFDFC), surfaceContainerLow = Color(0xFFF0F4F1),
            surfaceContainer = Color(0xFFE9EFEB), onSurface = Color(0xFF172520),
            onSurfaceVariant = Color(0xFF596960), secondaryContainer = Color(0xFFD8F1E4),
            onSecondaryContainer = Color(0xFF123E2D), outline = Color(0xFF75857B),
            outlineVariant = Color(0xFFD2DED6)
        )
    }

    MaterialTheme(
        colorScheme = downlyColors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(32.dp)
        )
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                when (state.tab) {
                                    1 -> "History"
                                    2 -> "Settings"
                                    else -> "Downly"
                                },
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                when (state.tab) {
                                    1 -> "Your downloads, all in one place"
                                    2 -> "Make Downly work for you"
                                    else -> "Your personal media saver"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        if (state.tab != 0) {
                            IconButton(onClick = { vm.setTab(0) }) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to Home")
                            }
                        }
                    },
                    actions = {
                        if (state.tab == 0) {
                            IconButton(onClick = { vm.setTab(1) }) {
                                Icon(Icons.Rounded.History, contentDescription = "Download history")
                            }
                            IconButton(onClick = { vm.setTab(2) }) {
                                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { padding ->
            when (state.tab) {
                0 -> DownloadScreen(state, vm, Modifier.padding(padding))
                1 -> ActivityScreen(state, vm, Modifier.padding(padding))
                else -> SettingsScreen(state, vm, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun DownloadScreen(
    state: ScreenState,
    vm: DownlyViewModel,
    modifier: Modifier
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Save what you love.",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Videos, audio and more — in a few taps.",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(DownlyGreen),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Movie, null, tint = Color.White, modifier = Modifier.size(27.dp)) }
                        Box(
                            Modifier.size(42.dp).align(Alignment.End).clip(RoundedCornerShape(13.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.MusicNote, null, tint = DownlyGreen) }
                    }
                }
            }
        }
        item {
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Link, null, tint = DownlyGreen) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Paste a link", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("YouTube, Shorts and supported sites", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            if (text.isNotBlank()) vm.setUrl(text)
                            else Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Rounded.ContentPaste, "Paste URL") }
                    }
                    OutlinedTextField(
                        value = state.url, onValueChange = vm::setUrl, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://…") }, minLines = 2, shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { vm.fetchInfo() })
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(state.mode == MediaMode.VIDEO, { vm.setMode(MediaMode.VIDEO) },
                            label = { Text("Video") }, leadingIcon = { Icon(Icons.Rounded.Movie, null, Modifier.size(17.dp)) })
                        FilterChip(state.mode == MediaMode.AUDIO, { vm.setMode(MediaMode.AUDIO) },
                            label = { Text("MP3 audio") }, leadingIcon = { Icon(Icons.Rounded.MusicNote, null, Modifier.size(17.dp)) })
                        Spacer(Modifier.weight(1f))
                    }
                    Button(
                        onClick = vm::fetchInfo, modifier = Modifier.fillMaxWidth().height(51.dp),
                        enabled = !state.busy && state.url.isNotBlank(), shape = MaterialTheme.shapes.medium
                    ) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.VideoLibrary, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.busy) "Working…" else "Fetch media info", fontWeight = FontWeight.Bold)
                    }
                    if (state.message.isNotBlank()) Text(state.message, color = if (state.message.contains("failed", true) || state.message.contains("invalid", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            state.preview?.let { preview ->
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(width = 112.dp, height = 76.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center) {
                                Icon(if (state.mode == MediaMode.VIDEO) Icons.Rounded.Movie else Icons.Rounded.MusicNote, null,
                                    tint = DownlyGreen, modifier = Modifier.size(25.dp))
                                if (!preview.thumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(model = preview.thumbnailUrl, contentDescription = "Media thumbnail",
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(preview.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(listOf(preview.uploader, preview.duration).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Media ready" },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.AutoAwesome, null, tint = DownlyGreen)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Smart Download", fontWeight = FontWeight.Bold)
                                        Text(if (state.mode == MediaMode.AUDIO) (if (state.connectionType == "Mobile data") "Mobile data · Smaller MP3 file" else state.connectionType + " · Best MP3 quality") else state.connectionType + " · " + state.recommendedQuality + " recommended", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (state.connectionType == "Wi-Fi" || state.connectionType == "Ethernet") Icon(Icons.Rounded.Wifi, null, tint = DownlyGreen) else Icon(Icons.Rounded.SignalCellularAlt, null, tint = DownlyGreen)
                                }
                                if (state.mode == MediaMode.VIDEO) {
                                    val estimate = preview.qualityOptions.firstOrNull { it.quality == state.recommendedQuality }?.estimatedBytes
                                    Text("Suggested " + state.recommendedQuality + (estimate?.let { " · about " + formatBytes(it) } ?: " · size depends on source"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                                Button(onClick = { vm.startDownload() }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) {
                                    Icon(Icons.Rounded.AutoAwesome, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (state.mode == MediaMode.AUDIO) "Smart MP3 Download" else "Smart Download · " + state.recommendedQuality, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (state.mode == MediaMode.VIDEO && preview.qualityOptions.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("AVAILABLE VIDEO QUALITIES", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = DownlyGreen)
                                    Spacer(Modifier.weight(1f))
                                    Text("${preview.qualityOptions.size} options", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                preview.qualityOptions.forEach { option ->
                                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(
                                        containerColor = if (option.quality == state.recommendedQuality) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                                    )) {
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(option.quality + if (option.quality == state.recommendedQuality) " · Recommended" else "", fontWeight = FontWeight.Bold)
                                                Text(option.estimatedBytes?.let { "About " + formatBytes(it) } ?: "File size varies by source", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            OutlinedButton(onClick = { vm.startDownload(option.quality) }) { Text("Download") }
                                        }
                                    }
                                }
                            }
                        }
                        if (state.mode == MediaMode.AUDIO && preview.audioOptions.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("AVAILABLE AUDIO OPTIONS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = DownlyGreen)
                                    Spacer(Modifier.weight(1f))
                                    Text("${preview.audioOptions.size} options", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                preview.audioOptions.forEach { option ->
                                    Card(
                                        shape = MaterialTheme.shapes.medium,
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Text(option.quality, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    option.estimatedBytes?.let { "About " + formatBytes(it) } ?: "File size varies by source",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            OutlinedButton(
                                                onClick = { vm.startDownload("audio:${option.formatId}") },
                                                shape = MaterialTheme.shapes.medium,
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                                            ) { Text("Download") }
                                        }
                                    }
                                }
                            }
                        }
                        Text("Save only content you own or have permission to download.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityScreen(state: ScreenState, vm: DownlyViewModel, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your downloads", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${state.history.count { it.status == DownloadStatus.COMPLETE }} completed · ${state.history.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }} active", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(state.history.size.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = DownlyGreen)
            }
        }
        if (state.history.isNotEmpty()) item {
            OutlinedButton(onClick = vm::clearFinished, modifier = Modifier.fillMaxWidth()) { Text("Clear finished and failed") }
        }
        if (state.history.isEmpty()) item {
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.History, null, tint = DownlyGreen, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Nothing here yet", fontWeight = FontWeight.Bold)
                    Text("Your queue and download history will show here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(state.history, key = { it.id }) { DownloadCard(it, vm) }
    }
}

@Composable
private fun DownloadCard(record: DownloadRecord, vm: DownlyViewModel) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center) {
                    Icon(if (record.status == DownloadStatus.COMPLETE) Icons.Rounded.CheckCircle else Icons.Rounded.Download, null, tint = DownlyGreen)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(record.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(record.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (record.status == DownloadStatus.QUEUED || record.status == DownloadStatus.DOWNLOADING) {
                    IconButton(onClick = { vm.cancel(record) }) { Icon(Icons.Rounded.Close, "Cancel download") }
                } else {
                    IconButton(onClick = { vm.delete(record) }) { Text("×", style = MaterialTheme.typography.titleLarge) }
                }
            }
            if (record.status == DownloadStatus.QUEUED || record.status == DownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(progress = { record.progress }, modifier = Modifier.fillMaxWidth(), color = DownlyGreen)
                Text("${(record.progress * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            if (record.filePath != null && record.status == DownloadStatus.COMPLETE) {
                Text("Saved: ${File(record.filePath).name}", color = DownlyGreen, style = MaterialTheme.typography.labelSmall)
                OutlinedButton(onClick = {
                    Toast.makeText(DownlyContextRef.context, "Saved to the Downly folder in app-specific storage.", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Show download location") }
            }
            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.timestamp)),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SettingsScreen(state: ScreenState, vm: DownlyViewModel, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Appearance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Choose how Downly looks on your device.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(state.themeMode == "System", { vm.setThemeMode("System") }, label = { Text("System") })
                    FilterChip(state.themeMode == "Light", { vm.setThemeMode("Light") }, label = { Text("Light") })
                    FilterChip(state.themeMode == "Dark", { vm.setThemeMode("Dark") }, label = { Text("Dark") })
                }
            }
        }

    }
}


private fun buildQualityOptions(formats: List<VideoFormat>): List<QualityOption> {
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

private fun buildAudioOptions(formats: List<VideoFormat>): List<AudioOption> {
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
        // Different format IDs can describe the same audio quality; show each bitrate/container once.
        .distinctBy { "${it.bitrate}:${it.quality.substringAfter(" · ")}" }
}

private fun defaultQualityFor(connectionType: String): String = when (connectionType) {
    "Wi-Fi", "Ethernet" -> "1080p"
    "Mobile data" -> "480p"
    else -> "360p"
}

private fun recommendQuality(options: List<QualityOption>, connectionType: String): String {
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

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
    else -> bytes.toString() + " B"
}

private fun appContextOpen(intent: Intent) {
    runCatching {
        val context = DownlyContextRef.context ?: return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private object DownlyContextRef {
    var context: Context? = null
}

private fun isValidUrl(value: String): Boolean {
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
    return uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
}

private fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remaining)
    else String.format(Locale.US, "%d:%02d", minutes, remaining)
}

private fun friendlyError(error: Throwable): String {
    val message = error.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
    return when {
        message.isBlank() -> "Something went wrong. Check your connection and try again."
        message.contains("unsupported url", true) -> "This link is not supported by yt-dlp."
        message.contains("private", true) || message.contains("sign in", true) -> "This media may require sign-in or may be private."
        message.contains("network", true) || message.contains("resolve host", true) || message.contains("connection", true) -> "Network problem. Check your connection and retry."
        else -> message.take(220)
    }
}

private fun encodeText(value: String): String =
    android.util.Base64.encodeToString(value.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)

private fun decodeText(value: String): String =
    String(android.util.Base64.decode(value, android.util.Base64.DEFAULT), Charsets.UTF_8)
