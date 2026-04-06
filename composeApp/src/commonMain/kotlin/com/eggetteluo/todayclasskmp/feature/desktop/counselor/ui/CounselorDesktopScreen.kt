package com.eggetteluo.todayclasskmp.feature.desktop.counselor.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.eggetteluo.todayclasskmp.feature.desktop.components.RoleDesktopScaffold
import com.eggetteluo.todayclasskmp.feature.desktop.model.DesktopTab

private val counselorTabs = listOf(
    DesktopTab(topTitle = "今日巡查", bottomLabel = "看今日", icon = Icons.Outlined.Today),
    DesktopTab(topTitle = "班级总览", bottomLabel = "班级", icon = Icons.Outlined.List),
    DesktopTab(topTitle = "设置", bottomLabel = "设置", icon = Icons.Outlined.Settings),
)

@Composable
fun CounselorDesktopScreen() {
    RoleDesktopScaffold(
        roleLabel = "辅导员",
        tabs = counselorTabs,
        topBarContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
        topBarContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )
}
