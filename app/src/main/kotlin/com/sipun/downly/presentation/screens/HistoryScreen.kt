package com.sipun.downly.presentation.screens

import com.sipun.downly.R

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sipun.downly.data.DownloadRecord
import com.sipun.downly.data.DownloadStatus
import com.sipun.downly.data.ScreenState
import com.sipun.downly.presentation.DownlyViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class HistoryFilter { ALL, VIDEO, AUDIO }

@Composable
fun ActivityScreen(state: ScreenState, vm: DownlyViewModel, modifier: Modifier) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    val completed = state.history.count { it.status == DownloadStatus.COMPLETE }
    val active = state.history.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
    val visibleRecords = state.history.filter { record ->
        val audio = isAudioRecord(record)
        val matchesFilter = when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.VIDEO -> !audio
            HistoryFilter.AUDIO -> audio
        }
        matchesFilter && (search.isBlank() || record.title.contains(search, true) ||
            record.filePath.orEmpty().contains(search, true) || record.detail.contains(search, true))
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OutlinedTextField(
                value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(50),
                placeholder = { Text(stringResource(R.string.search_downloads)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HistoryFilterChip("All (" + state.history.size + ")", filter == HistoryFilter.ALL, Modifier.weight(1f)) { filter = HistoryFilter.ALL }
                HistoryFilterChip("Videos (" + state.history.count { !isAudioRecord(it) } + ")", filter == HistoryFilter.VIDEO, Modifier.weight(1f), Icons.Rounded.VideoFile) { filter = HistoryFilter.VIDEO }
                HistoryFilterChip("Audio (" + state.history.count { isAudioRecord(it) } + ")", filter == HistoryFilter.AUDIO, Modifier.weight(1f), Icons.Rounded.MusicNote) { filter = HistoryFilter.AUDIO }
            }
        }
        if (state.history.any { it.status == DownloadStatus.COMPLETE || it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }) {
            item {
                OutlinedButton(onClick = vm::clearFinished, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50), contentPadding = PaddingValues(vertical = 14.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.clear_finished_failed), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (visibleRecords.isEmpty()) item {
            Box(
                modifier = Modifier.fillParentMaxHeight(0.52f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(76.dp).clip(RoundedCornerShape(26.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = null,
                                tint = colorResource(R.color.downly_accent),
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Text(
                            if (state.history.isEmpty()) "Your library is waiting" else "No downloads found",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (state.history.isEmpty()) "Videos and audio you download will show up here." else "Try a different search or switch the media filter.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (state.history.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Icon(Icons.Rounded.Search, contentDescription = null, tint = colorResource(R.color.downly_accent), modifier = Modifier.size(17.dp))
                                    Text(stringResource(R.string.paste_link_to_start), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        items(visibleRecords, key = { it.id }) { record -> DownloadCard(record, vm) }
    }
}

@Composable
private fun HistoryFilterChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(label, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun DownloadCard(record: DownloadRecord, vm: DownlyViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val audio = isAudioRecord(record)
    val fileName = record.filePath?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: record.title
    val folder = if (audio) "Downloads/Downly/Music" else "Downloads/Downly/Videos"
    val details by produceState(initialValue = MediaCardDetails(), key1 = record.filePath) {
        value = withContext(Dispatchers.IO) { resolveMediaDetails(context, record.filePath, audio) }
    }
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(width = 116.dp, height = 104.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (audio) androidx.compose.ui.graphics.Color(0xFFEBDDFF) else MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (details.artwork != null) {
                        Image(details.artwork!!.asImageBitmap(), contentDescription = stringResource(R.string.media_artwork),
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(if (audio) Icons.Rounded.MusicNote else Icons.Rounded.VideoFile,
                            contentDescription = if (audio) stringResource(R.string.audio_file) else stringResource(R.string.video_file),
                            tint = if (audio) androidx.compose.ui.graphics.Color(0xFF8754B4) else colorResource(R.color.downly_accent),
                            modifier = Modifier.size(42.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(fileName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Rounded.Folder, contentDescription = null, tint = colorResource(R.color.downly_accent), modifier = Modifier.size(19.dp))
                        Text(folder, color = colorResource(R.color.downly_accent), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (record.status == DownloadStatus.QUEUED || record.status == DownloadStatus.DOWNLOADING) {
                    IconButton(onClick = { vm.cancel(record) }) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel_download)) }
                } else {
                    IconButton(onClick = { vm.delete(record) }) { Icon(Icons.Rounded.Close, stringResource(R.string.remove_from_history)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(details.fileType.ifBlank { if (audio) stringResource(R.string.media_mp3) else stringResource(R.string.media_video) }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.Storage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(details.size, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.timestamp)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (record.status == DownloadStatus.QUEUED || record.status == DownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(progress = { record.progress }, modifier = Modifier.fillMaxWidth(), color = colorResource(R.color.downly_accent))
                Text(record.progress.times(100).toInt().toString() + "% · " + record.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = { vm.pause(record) },
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pause_download))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        when (record.status) {
                            DownloadStatus.COMPLETE -> Icons.Rounded.CheckCircle
                            DownloadStatus.FAILED -> Icons.Rounded.Refresh
                            else -> Icons.Rounded.Download
                        },
                        contentDescription = null,
                        tint = if (record.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else colorResource(R.color.downly_accent),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(record.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (record.status == DownloadStatus.FAILED || record.status == DownloadStatus.PAUSED) {
                    Button(
                        onClick = { if (record.status == DownloadStatus.FAILED) vm.retry(record) else vm.resume(record) },
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.downly_accent))
                    ) {
                        Icon(if (record.status == DownloadStatus.FAILED) Icons.Rounded.Refresh else Icons.Rounded.PlayArrow,
                            contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (record.status == DownloadStatus.FAILED) R.string.retry_download else R.string.resume_download))
                    }
                }
            }
            if (record.status == DownloadStatus.COMPLETE && record.filePath != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { showInFolder(context, record.filePath, audio) },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
                        Icon(Icons.Rounded.Folder, null, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(stringResource(R.string.show_in_folder), maxLines = 1)
                    }
                    Button(onClick = { playMedia(context, record.filePath, audio) },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.downly_accent)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.play_action), maxLines = 1)
                    }
                }
            }
        }
    }
}

private data class MediaCardDetails(val artwork: Bitmap? = null, val size: String = "—", val fileType: String = "")

private fun isAudioRecord(record: DownloadRecord): Boolean {
    val path = record.filePath.orEmpty().lowercase(Locale.ROOT)
    return path.endsWith(".mp3") || path.endsWith(".m4a") || path.endsWith(".aac") ||
        path.endsWith(".opus") || path.endsWith(".ogg") || path.endsWith(".wav") || path.startsWith("Music/")
}

private fun resolveMediaDetails(context: Context, path: String?, audio: Boolean): MediaCardDetails {
    if (path.isNullOrBlank()) return MediaCardDetails(fileType = if (audio) "Audio" else "Video")
    val name = path.substringAfterLast('/')
    val relativePath = path.substringBeforeLast('/', if (audio) "Download/Downly/Music" else "Download/Downly/Videos") + "/"
    val collection = if (audio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    var artwork: Bitmap? = null
    var size = "—"
    var mime = name.substringAfterLast('.', "").uppercase(Locale.ROOT)
    runCatching {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE)
        val selection = MediaStore.MediaColumns.DISPLAY_NAME + " = ? AND " + MediaStore.MediaColumns.RELATIVE_PATH + " = ?"
        context.contentResolver.query(collection, projection, selection, arrayOf(name, relativePath), null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                size = formatSize(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)))
                mime = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))?.substringAfter('/')?.uppercase(Locale.ROOT) ?: mime
                val uri = ContentUris.withAppendedId(collection, id)
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val bytes = if (audio) retriever.getEmbeddedPicture() else null
                    artwork = if (bytes != null) android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    else if (!audio) retriever.getFrameAtTime() else null
                } finally { retriever.release() }
            }
        }
    }
    return MediaCardDetails(artwork, size, mime)
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.ROOT, "%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format(Locale.ROOT, "%.0f KB", bytes / 1_000.0)
    else -> bytes.toString() + " B"
}

private fun findMediaUri(context: Context, path: String, audio: Boolean): Uri? {
    val name = path.substringAfterLast('/')
    val relativePath = path.substringBeforeLast('/', if (audio) "Download/Downly/Music" else "Download/Downly/Videos") + "/"
    val collection = if (audio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    return runCatching {
        val selection = MediaStore.MediaColumns.DISPLAY_NAME + " = ? AND " + MediaStore.MediaColumns.RELATIVE_PATH + " = ?"
        context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, arrayOf(name, relativePath), null)?.use { cursor ->
            if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))) else null
        }
    }.getOrNull()
}

private fun playMedia(context: Context, path: String, audio: Boolean) {
    val uri = findMediaUri(context, path, audio)
    if (uri == null) {
        Toast.makeText(context, context.getString(R.string.file_not_found_media_library), Toast.LENGTH_LONG).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, if (audio) "audio/*" else "video/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, context.getString(R.string.no_app_to_play_file), Toast.LENGTH_LONG).show()
    }
}

private fun showInFolder(context: Context, path: String, audio: Boolean) {
    val relative = path.substringBeforeLast('/', if (audio) "Music/Downly" else "Movies/Downly")
    val folderUri = Uri.parse("content://com.android.externalstorage.documents/document/" + Uri.encode("primary:" + relative))
    val viewIntent = Intent(Intent.ACTION_VIEW).setDataAndType(folderUri, DocumentsContract.Document.MIME_TYPE_DIR)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        if (viewIntent.resolveActivity(context.packageManager) != null) {
            runCatching { context.startActivity(viewIntent) }.getOrElse {
                val picker = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) picker.putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri)
                context.startActivity(picker)
            }
        } else {
            val picker = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) picker.putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri)
            context.startActivity(picker)
        }
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.saved_to_open_file_manager, relative), Toast.LENGTH_LONG).show()
    }
}
