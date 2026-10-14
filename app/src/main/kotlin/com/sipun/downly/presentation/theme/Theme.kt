package com.sipun.downly.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.sipun.downly.R

@Composable
fun DownlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) {
        darkColorScheme(
            primary = colorResource(R.color.downly_dark_primary),
            onPrimary = colorResource(R.color.downly_dark_on_primary),
            primaryContainer = colorResource(R.color.downly_dark_primary_container),
            onPrimaryContainer = colorResource(R.color.downly_dark_on_primary_container),
            secondary = colorResource(R.color.downly_dark_secondary),
            onSecondary = colorResource(R.color.downly_dark_on_secondary),
            background = colorResource(R.color.downly_dark_background),
            surface = colorResource(R.color.downly_dark_surface),
            surfaceContainerLow = colorResource(R.color.downly_dark_surface_container_low),
            surfaceContainer = colorResource(R.color.downly_dark_surface_container),
            onSurface = colorResource(R.color.downly_dark_on_surface),
            onSurfaceVariant = colorResource(R.color.downly_dark_on_surface_variant),
            secondaryContainer = colorResource(R.color.downly_dark_secondary_container),
            onSecondaryContainer = colorResource(R.color.downly_dark_on_secondary_container),
            outline = colorResource(R.color.downly_dark_outline),
            outlineVariant = colorResource(R.color.downly_dark_outline_variant)
        )
    } else {
        lightColorScheme(
            primary = colorResource(R.color.downly_light_primary),
            onPrimary = colorResource(R.color.downly_light_on_primary),
            primaryContainer = colorResource(R.color.downly_light_primary_container),
            onPrimaryContainer = colorResource(R.color.downly_light_on_primary_container),
            secondary = colorResource(R.color.downly_light_secondary),
            onSecondary = colorResource(R.color.downly_light_on_secondary),
            background = colorResource(R.color.downly_light_background),
            surface = colorResource(R.color.downly_light_surface),
            surfaceContainerLow = colorResource(R.color.downly_light_surface_container_low),
            surfaceContainer = colorResource(R.color.downly_light_surface_container),
            onSurface = colorResource(R.color.downly_light_on_surface),
            onSurfaceVariant = colorResource(R.color.downly_light_on_surface_variant),
            secondaryContainer = colorResource(R.color.downly_light_secondary_container),
            onSecondaryContainer = colorResource(R.color.downly_light_on_secondary_container),
            outline = colorResource(R.color.downly_light_outline),
            outlineVariant = colorResource(R.color.downly_light_outline_variant)
        )
    }

    val shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp)
    )

    MaterialTheme(
        colorScheme = colors,
        shapes = shapes,
        typography = Typography,
        content = content
    )
}
