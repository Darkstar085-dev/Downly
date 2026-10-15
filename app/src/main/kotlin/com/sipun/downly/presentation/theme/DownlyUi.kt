package com.sipun.downly.presentation.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object DownlyUiShapes {
    val card = RoundedCornerShape(20.dp)
    val nestedCard = RoundedCornerShape(16.dp)
    val dialog = RoundedCornerShape(28.dp)
}

@Composable
fun downlyCardBorder(selected: Boolean = false): BorderStroke {
    return BorderStroke(
        width = if (selected) 1.5.dp else 1.dp,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
fun Modifier.downlyDialogBorder(): Modifier {
    return border(
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        DownlyUiShapes.dialog
    )
}
