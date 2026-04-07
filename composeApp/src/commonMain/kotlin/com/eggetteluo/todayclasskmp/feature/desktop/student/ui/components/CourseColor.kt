package com.eggetteluo.todayclasskmp.feature.desktop.student.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

@Composable
fun rememberCourseColor(name: String): Color {
    return remember(name) {
        val hash = name.hashCode().toUInt().toLong()
        val hue = (hash % 360L).toFloat()
        val saturation = (0.60f + ((hash shr 8) % 18L).toFloat() / 100f).coerceIn(0.55f, 0.78f)
        val lightness = (0.45f + ((hash shr 16) % 16L).toFloat() / 100f).coerceIn(0.40f, 0.62f)
        Color.hsl(hue = hue, saturation = saturation, lightness = lightness)
    }
}
