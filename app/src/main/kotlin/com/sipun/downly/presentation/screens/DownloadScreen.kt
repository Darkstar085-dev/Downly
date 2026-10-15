package com.sipun.downly.presentation.screens

import com.sipun.downly.R

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.sipun.downly.data.MediaMode
import com.sipun.downly.data.ScreenState
import com.sipun.downly.presentation.DownlyViewModel
import com.sipun.downly.presentation.theme.DownlyUiShapes
import com.sipun.downly.presentation.theme.downlyCardBorder
import com.sipun.downly.utils.formatBytes
import com.sipun.downly.utils.recommendAudioOption

@Composable
fun DownloadScreen(
    state: ScreenState,
    vm: DownlyViewModel,
    modifier: Modifier
) {
    val context = LocalContext.current
    var compactReadyStatus by remember { mutableStateOf(false) }
    var linkInputExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(state.preview) {
        linkInputExpanded = state.preview == null
    }
    LaunchedEffect(state.message) {
        compactReadyStatus = state.message.equals("Media details ready", ignoreCase = true)
        if (state.message.equals("Downloader ready", ignoreCase = true)) {
            delay(4_500)
            compactReadyStatus = true
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Card(shape = DownlyUiShapes.card, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    if (state.preview != null && !linkInputExpanded) {
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { linkInputExpanded = true },
                            shape = DownlyUiShapes.card,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(stringResource(R.string.download_link_fetched), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(R.string.download_change_link_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(stringResource(R.string.download_change_link), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Link, null, tint = colorResource(R.color.downly_accent)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.download_paste_link), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.download_supported_sites), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            if (text.isNotBlank()) vm.setUrl(text.trim())
                            else Toast.makeText(context, context.getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Rounded.ContentPaste, stringResource(R.string.paste_url)) }
                    }
                    OutlinedTextField(
                        value = state.url, onValueChange = vm::setUrl, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.url_placeholder)) }, minLines = 2, shape = DownlyUiShapes.card,
                        trailingIcon = {
                            if (state.url.isNotBlank()) {
                                IconButton(onClick = { vm.setUrl("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_url))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { vm.fetchInfo() })
                    )
                    if (state.preview == null) MediaModeSelector(state, vm)
                    Button(
                        onClick = vm::fetchInfo, modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !state.busy && state.url.isNotBlank(), shape = DownlyUiShapes.card,
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        )
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            when {
                                state.message.contains("Libraries", ignoreCase = true) ||
                                    state.message.contains("Preparing downloader", ignoreCase = true) -> stringResource(R.string.preparing_downloader)
                                state.busy -> "Fetching media…"
                                else -> "Fetch Media"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    }
                    if (state.message.isNotBlank()) {
                        val isError = listOf("failed", "invalid", "error", "missing", "unable", "not found")
                            .any { state.message.contains(it, ignoreCase = true) }
                        val isInitializing = state.message.contains("Libraries", ignoreCase = true) ||
                            state.message.contains("Preparing Libraries", ignoreCase = true)
                        val isReady = state.message.equals("Downloader ready", ignoreCase = true) || state.message.equals("Media details ready", ignoreCase = true)
                        val isInfo = isInitializing || isReady
                        if (isReady && compactReadyStatus) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    if (state.message.equals("Media details ready", ignoreCase = true)) "Media details ready" else "Downloader ready",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = DownlyUiShapes.card,
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                                    isReady -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
                                    isInitializing -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f)
                                    else -> MaterialTheme.colorScheme.surfaceContainer
                                }
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when {
                                    isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
                                    isInfo -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(
                                        when {
                                            isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                            isReady -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                        }
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        isError -> Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        isReady -> Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        isInitializing -> Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        else -> Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        when {
                                            isError -> "Something needs attention"
                                            isInitializing && state.message.contains("Downloading", ignoreCase = true) -> stringResource(R.string.setting_up_downly)
                                            isInitializing -> "Getting things ready"
                                            isReady -> "Ready to download"
                                            else -> state.message
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when {
                                            isError -> MaterialTheme.colorScheme.onErrorContainer
                                            isReady -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        when {
                                            isError -> state.message
                                            isInitializing && state.message.contains("Downloading", ignoreCase = true) ->
                                                "Preparing the download engine. This may take a moment on first launch."
                                            isInitializing ->
                                                "Checking the required components for your first download."
                                            isReady ->
                                                "Paste a video or audio link above to get started."
                                            else -> state.message
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            isError -> MaterialTheme.colorScheme.onErrorContainer
                                            isReady -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    if (isInitializing) {
                                        Spacer(Modifier.height(3.dp))
                                        val progress = state.libraryProgress
                                        if (progress == null) {
                                            androidx.compose.material3.LinearProgressIndicator(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            )
                                        } else {
                                            androidx.compose.material3.LinearProgressIndicator(
                                                progress = { progress.coerceIn(0f, 1f) },
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            )
                                        }
                                        if (state.message.contains("Downloading", ignoreCase = true) && progress != null) {
                                            Text(
                                                "${(progress * 100).toInt().coerceIn(0, 100)}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.align(Alignment.End)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
        item {
            state.preview?.let { preview ->
                Card(shape = DownlyUiShapes.card, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(width = 112.dp, height = 76.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center) {
                                Icon(if (state.mode == MediaMode.VIDEO) Icons.Rounded.Movie else Icons.Rounded.MusicNote, null,
                                    tint = colorResource(R.color.downly_accent), modifier = Modifier.size(25.dp))
                                if (!preview.thumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(model = preview.thumbnailUrl, contentDescription = stringResource(R.string.media_thumbnail),
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(preview.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOf(preview.uploader, preview.duration).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Media ready" },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        MediaModeSelector(state, vm)
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = DownlyUiShapes.card, border = downlyCardBorder()) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.AutoAwesome, null, tint = colorResource(R.color.downly_accent))
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(R.string.best_download), fontWeight = FontWeight.Bold)
                                        val recommendedAudio = recommendAudioOption(preview.audioOptions)
                                        Text(
                                            if (state.mode == MediaMode.AUDIO) {
                                                recommendedAudio?.let { "Highest available · " + it.quality }
                                                    ?: stringResource(R.string.best_audio_quality)
                                            } else "Highest available · " + state.recommendedQuality,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (state.connectionType == "Wi-Fi" || state.connectionType == "Ethernet") Icon(Icons.Rounded.Wifi, null, tint = colorResource(R.color.downly_accent)) else Icon(Icons.Rounded.SignalCellularAlt, null, tint = colorResource(R.color.downly_accent))
                                }
                                if (state.mode == MediaMode.VIDEO) {
                                    val recommendedVideo = preview.qualityOptions.firstOrNull { it.quality == state.recommendedQuality }
                                    Text(
                                        recommendedVideo?.estimatedBytes?.let { bytes ->
                                            (if (recommendedVideo.sizeIsApproximate) "About " else "") + formatBytes(bytes)
                                        } ?: stringResource(R.string.file_size_varies),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else {
                                    val recommendedAudio = recommendAudioOption(preview.audioOptions)
                                    Text(
                                        recommendedAudio?.estimatedBytes?.let { bytes ->
                                            (if (recommendedAudio.sizeIsApproximate) "About " else "") + formatBytes(bytes)
                                        } ?: stringResource(R.string.file_size_varies),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Button(onClick = vm::startBestDownload, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) {
                                    Text(if (state.mode == MediaMode.AUDIO) stringResource(R.string.best_audio_download) else stringResource(R.string.download_best_quality, state.recommendedQuality), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (state.mode == MediaMode.VIDEO && preview.qualityOptions.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.available_video_qualities), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = colorResource(R.color.downly_accent))
                                    Spacer(Modifier.weight(1f))
                                    Text(pluralStringResource(R.plurals.options_count, preview.qualityOptions.size, preview.qualityOptions.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                preview.qualityOptions.forEach { option ->
                                    Card(
                                        shape = DownlyUiShapes.card,
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (option.quality == state.recommendedQuality) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (option.quality == state.recommendedQuality) colorResource(R.color.downly_accent) else MaterialTheme.colorScheme.outlineVariant
                                        )
                                    ) {
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(option.quality + if (option.quality == state.recommendedQuality) " · " + stringResource(R.string.recommended) else "", fontWeight = FontWeight.Bold)
                                                Text(option.estimatedBytes?.let { if (option.sizeIsApproximate) stringResource(R.string.file_size_about, formatBytes(it)) else formatBytes(it) } ?: stringResource(R.string.file_size_varies), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            OutlinedButton(
                                                onClick = { vm.startDownload("video:" + option.formatSelector) },
                                                shape = DownlyUiShapes.card,
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp)
                                            ) { Text(stringResource(R.string.download_action)) }
                                        }
                                    }
                                }
                            }
                        }
                        if (state.mode == MediaMode.AUDIO && preview.audioOptions.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.available_audio_options), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = colorResource(R.color.downly_accent))
                                    Spacer(Modifier.weight(1f))
                                    Text(pluralStringResource(R.plurals.options_count, preview.audioOptions.size, preview.audioOptions.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val bestAudioFormatId = recommendAudioOption(preview.audioOptions)?.formatId
                                preview.audioOptions.forEach { option ->
                                    val isBestAudio = option.formatId == bestAudioFormatId
                                    Card(
                                        shape = DownlyUiShapes.card,
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isBestAudio) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isBestAudio) colorResource(R.color.downly_accent) else MaterialTheme.colorScheme.outlineVariant
                                        )
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
                                                Text(option.quality + if (isBestAudio) " · " + stringResource(R.string.recommended) else "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = if (isBestAudio) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
                                                Text(
                                                    option.estimatedBytes?.let { if (option.sizeIsApproximate) stringResource(R.string.file_size_about, formatBytes(it)) else formatBytes(it) } ?: stringResource(R.string.file_size_varies),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            OutlinedButton(
                                                onClick = { vm.startDownload("audio:${option.formatId}") },
                                                shape = DownlyUiShapes.card,
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                                            ) { Text(stringResource(R.string.download_action)) }
                                        }
                                    }
                                }
                            }
                        }
                        Text(stringResource(R.string.download_permission_note), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}


@Composable
private fun MediaModeSelector(state: ScreenState, vm: DownlyViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = DownlyUiShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val selectedVideo = state.mode == MediaMode.VIDEO
            Row(
                modifier = Modifier.weight(1f).height(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selectedVideo) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { vm.setMode(MediaMode.VIDEO) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Movie, null, tint = if (selectedVideo) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.media_video), color = if (selectedVideo) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (selectedVideo) FontWeight.SemiBold else FontWeight.Medium)
            }
            Box(
                Modifier.padding(horizontal = 4.dp).width(1.dp).height(28.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            val selectedAudio = state.mode == MediaMode.AUDIO
            Row(
                modifier = Modifier.weight(1f).height(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selectedAudio) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { vm.setMode(MediaMode.AUDIO) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.MusicNote, null, tint = if (selectedAudio) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.media_audio), color = if (selectedAudio) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (selectedAudio) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}
