package com.sipun.downly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DownlyDarkColors = darkColorScheme(
    primary = Color(0xFF79D8B0), onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF075C3D), onPrimaryContainer = Color(0xFFB8F5D8),
    secondary = Color(0xFFB3CCBF), onSecondary = Color(0xFF20372C),
    background = Color(0xFF101512), surface = Color(0xFF171E19),
    surfaceContainerLow = Color(0xFF1D2721), surfaceContainer = Color(0xFF232D27),
    onSurface = Color(0xFFE7EEE9), onSurfaceVariant = Color(0xFFA8B4AC),
    secondaryContainer = Color(0xFF294438), onSecondaryContainer = Color(0xFFD5F4E4),
    outline = Color(0xFF607067), outlineVariant = Color(0xFF3A4940)
)

private val DownlyLightColors = lightColorScheme(
    primary = Color(0xFF087B56), onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F0D6), onPrimaryContainer = Color(0xFF002113),
    secondary = Color(0xFF426657), onSecondary = Color.White,
    background = Color(0xFFF6F8F6), surface = Color(0xFFFCFDFC),
    surfaceContainerLow = Color(0xFFF0F4F1), surfaceContainer = Color(0xFFE9EFEB),
    onSurface = Color(0xFF172520), onSurfaceVariant = Color(0xFF596960),
    secondaryContainer = Color(0xFFD8F1E4), onSecondaryContainer = Color(0xFF123E2D),
    outline = Color(0xFF75857B), outlineVariant = Color(0xFFD2DED6)
)

private val DownlyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun DownlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DownlyDarkColors else DownlyLightColors,
        shapes = DownlyShapes,
        typography = Typography,
        content = content
    )
}
