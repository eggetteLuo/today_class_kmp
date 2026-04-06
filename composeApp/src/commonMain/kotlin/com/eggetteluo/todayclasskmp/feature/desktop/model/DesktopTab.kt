package com.eggetteluo.todayclasskmp.feature.desktop.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
data class DesktopTab(
    val topTitle: String,
    val bottomLabel: String,
    val icon: ImageVector,
)
