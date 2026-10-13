package com.sipun.downly.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sipun.downly.presentation.DownlyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownlyApp(vm: DownlyViewModel = viewModel()) {
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
