package com.sipun.downly.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sipun.downly.data.ScreenState
import com.sipun.downly.presentation.DownlyViewModel

@Composable
fun SettingsScreen(state: ScreenState, vm: DownlyViewModel, modifier: Modifier) {
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
