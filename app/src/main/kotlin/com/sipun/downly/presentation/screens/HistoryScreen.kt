package com.sipun.downly.presentation.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sipun.downly.data.DownloadRecord
import com.sipun.downly.data.DownloadStatus
import com.sipun.downly.data.ScreenState
import com.sipun.downly.presentation.DownlyViewModel
import com.sipun.downly.utils.DownlyContextRef
import com.sipun.downly.utils.DownlyGreen
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun ActivityScreen(state: ScreenState, vm: DownlyViewModel, modifier: Modifier) {
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
fun DownloadCard(record: DownloadRecord, vm: DownlyViewModel) {
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
