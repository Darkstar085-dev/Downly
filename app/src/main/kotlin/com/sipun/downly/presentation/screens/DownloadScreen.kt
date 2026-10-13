package com.sipun.downly.presentation.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sipun.downly.data.MediaMode
import com.sipun.downly.data.ScreenState
import com.sipun.downly.presentation.DownlyViewModel
import com.sipun.downly.utils.DownlyGreen
import com.sipun.downly.utils.formatBytes

@Composable
fun DownloadScreen(
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
