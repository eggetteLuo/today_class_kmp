package com.eggetteluo.todayclass.feature.desktop.teacher.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.eggetteluo.todayclass.feature.desktop.components.RoleDesktopScaffold
import com.eggetteluo.todayclass.feature.desktop.model.DesktopTab

private val teacherTabs = listOf(
    DesktopTab(topTitle = "今日教学", bottomLabel = "看今日", icon = Icons.Outlined.Today),
    DesktopTab(topTitle = "课程管理", bottomLabel = "课程", icon = Icons.Outlined.List),
    DesktopTab(topTitle = "设置", bottomLabel = "设置", icon = Icons.Outlined.Settings),
)

@Composable
fun TeacherDesktopScreen() {
    RoleDesktopScaffold(
        roleLabel = "老师",
        tabs = teacherTabs,
        topBarContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        topBarContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}
