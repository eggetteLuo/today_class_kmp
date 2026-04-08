package com.eggetteluo.todayclass.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.eggetteluo.todayclass.core.preferences.StudentSettingsPreferences

enum class ThemeAccent(
    val key: String,
    val label: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
) {
    Blue(
        key = "blue",
        label = "海洋蓝",
        primary = Color(0xFF2D6CDF),
        secondary = Color(0xFF3B7AF0),
        tertiary = Color(0xFF6C7BC7),
    ),
    Green(
        key = "green",
        label = "薄荷绿",
        primary = Color(0xFF1F8A5B),
        secondary = Color(0xFF2E9F6B),
        tertiary = Color(0xFF4CA08A),
    ),
    Orange(
        key = "orange",
        label = "活力橙",
        primary = Color(0xFFD96E1D),
        secondary = Color(0xFFE9853E),
        tertiary = Color(0xFFC9824E),
    ),
    Purple(
        key = "purple",
        label = "深空紫",
        primary = Color(0xFF6E4BC3),
        secondary = Color(0xFF7E5DD1),
        tertiary = Color(0xFF8C74C9),
    ),
    ;

    companion object {
        fun fromKey(key: String?): ThemeAccent {
            return entries.firstOrNull { it.key == key } ?: Blue
        }
    }
}

object AppThemeState {
    var currentAccent by mutableStateOf(ThemeAccent.fromKey(StudentSettingsPreferences.getThemeAccent()))
        private set

    fun setAccent(accent: ThemeAccent) {
        if (currentAccent == accent) return
        currentAccent = accent
        StudentSettingsPreferences.setThemeAccent(accent.key)
    }
}

fun createAppColorScheme(accent: ThemeAccent): ColorScheme {
    return lightColorScheme(
        primary = accent.primary,
        onPrimary = Color.White,
        primaryContainer = accent.primary.copy(alpha = 0.18f),
        onPrimaryContainer = Color(0xFF1A1A1A),
        secondary = accent.secondary,
        onSecondary = Color.White,
        secondaryContainer = accent.secondary.copy(alpha = 0.16f),
        onSecondaryContainer = Color(0xFF1A1A1A),
        tertiary = accent.tertiary,
        onTertiary = Color.White,
        tertiaryContainer = accent.tertiary.copy(alpha = 0.16f),
        onTertiaryContainer = Color(0xFF1A1A1A),
    )
}
