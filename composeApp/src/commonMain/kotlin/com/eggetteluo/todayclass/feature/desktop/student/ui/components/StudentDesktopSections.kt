@file:OptIn(ExperimentalMaterial3Api::class)

package com.eggetteluo.todayclass.feature.desktop.student.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eggetteluo.todayclass.feature.desktop.student.ui.TodayCoursesUiState
import com.eggetteluo.todayclass.feature.desktop.student.ui.WeekCoursesUiState

@Composable
internal fun TodayTopBar(
    todayUiState: TodayCoursesUiState,
    isTomorrow: Boolean,
    onToggleTomorrow: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    LargeTopAppBar(
        title = {
            Column {
                Text(text = if (isTomorrow) "明日预告" else "今日课表", fontWeight = FontWeight.ExtraBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = todayUiState.displayDateString,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(" · ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.padding(start = 2.dp),
                    ) {
                        Text(
                            text = "第 ${todayUiState.currentWeek ?: 1} 周",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            navigationIconContentColor = Color.Unspecified,
            titleContentColor = Color.Unspecified,
            actionIconContentColor = Color.Unspecified,
        ),
        actions = {
            FilterChip(
                selected = isTomorrow,
                onClick = onToggleTomorrow,
                label = { Text(if (isTomorrow) "返回今日" else "看明天") },
                leadingIcon = {
                    Icon(
                        imageVector = if (isTomorrow) Icons.Outlined.Today else Icons.AutoMirrored.Filled.EventNote,
                        modifier = Modifier.size(18.dp),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.padding(end = 16.dp),
            )
        },
    )
}

@Composable
internal fun WeekTopBar(
    weekUiState: WeekCoursesUiState,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onBackToCurrentWeek: () -> Unit,
    onFullScreen: () -> Unit,
) {
    LargeTopAppBar(
        title = {
            Column {
                Text("周课表总览", fontWeight = FontWeight.ExtraBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "一周课程分布",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(" · ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.padding(start = 2.dp),
                    ) {
                        Text(
                            text = "第 ${weekUiState.displayingWeek} 周",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            navigationIconContentColor = Color.Unspecified,
            titleContentColor = Color.Unspecified,
            actionIconContentColor = Color.Unspecified,
        ),
        actions = {
            IconButton(onClick = onPrevWeek) {
                Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一周")
            }
            IconButton(onClick = onNextWeek) {
                Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一周")
            }
            FilterChip(
                selected = false,
                onClick = onBackToCurrentWeek,
                label = { Text("本周") },
                modifier = Modifier.padding(end = 8.dp),
            )
            IconButton(onClick = onFullScreen) {
                Icon(imageVector = Icons.Outlined.OpenInFull, contentDescription = "全屏查看周课表")
            }
        },
    )
}

@Composable
internal fun SettingsTopBar(
    currentWeek: Int?,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    LargeTopAppBar(
        title = {
            Column {
                Text("设置", fontWeight = FontWeight.ExtraBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "个性化与课表管理",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(" · ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.padding(start = 2.dp),
                    ) {
                        Text(
                            text = "第 ${currentWeek ?: "-"} 周",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            navigationIconContentColor = Color.Unspecified,
            titleContentColor = Color.Unspecified,
            actionIconContentColor = Color.Unspecified,
        ),
        actions = {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(20.dp),
            )
        },
    )
}

@Composable
internal fun TodayCoursesContent(state: TodayCoursesUiState, timeTick: Long) {
    if (!state.termConfigured) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            EmptyStatePanel(
                title = "还没有课表数据",
                description = "先点击右下角“导入课表”，并选择当前周次后再查看课程",
                icon = Icons.Outlined.Add,
            )
        }
        return
    }
    if (state.courses.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            EmptyStatePanel(
                title = "第 ${state.currentWeek ?: "-"} 周${if (state.isTomorrow) "明天" else "今天"}没有课程",
                description = if (state.isTomorrow) {
                    "明天没课，奖励自己一把王者荣耀"
                } else {
                    "今天没课，去图书馆卷一下"
                },
                icon = Icons.AutoMirrored.Filled.EventNote,
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(state.courses, key = { it.id }) { course ->
            CourseItemCard(course = course, isToday = !state.isTomorrow, refreshTick = timeTick)
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
internal fun WeekCoursesContent(state: WeekCoursesUiState) {
    if (!state.termConfigured) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            EmptyStatePanel(
                title = "还没有课表数据",
                description = "先导入课表并设置周次，才能查看周课表",
                icon = Icons.Outlined.Add,
            )
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.courses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                EmptyStatePanel(
                    title = "第 ${state.displayingWeek} 周没有课程",
                    description = "尝试切换到其他周，或重新导入课表",
                    icon = Icons.Outlined.ViewWeek,
                )
            }
        } else {
            WeekTimetableBoard(courses = state.courses, modifier = Modifier.fillMaxWidth())
        }
        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
internal fun StudentSettingsContent(
    showImportButton: Boolean,
    onShowImportButtonChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "课程导入按钮显示",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "关闭后将隐藏右下角导入课表按钮",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = showImportButton,
                    onCheckedChange = onShowImportButtonChange,
                )
            }
        }
    }
}

@Composable
internal fun WeekTimetableFullScreen(
    state: WeekCoursesUiState,
    onDismiss: () -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onBackToCurrentWeek: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("周课表全屏", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Outlined.CloseFullscreen, contentDescription = "退出全屏")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onPrevWeek) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一周")
                }
                IconButton(onClick = onNextWeek) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一周")
                }
                FilterChip(selected = false, onClick = onBackToCurrentWeek, label = { Text("本周") })
                Text(
                    text = "第 ${state.displayingWeek} 周",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.courses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyStatePanel(
                        title = "第 ${state.displayingWeek} 周没有课程",
                        description = "尝试切换到其他周，或返回后重新导入课表",
                        icon = Icons.Outlined.ViewWeek,
                    )
                }
            } else {
                WeekTimetableBoard(courses = state.courses, modifier = Modifier.fillMaxWidth())
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EmptyStatePanel(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
