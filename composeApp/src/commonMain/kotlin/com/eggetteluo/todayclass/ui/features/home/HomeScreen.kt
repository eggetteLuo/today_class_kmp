package com.eggetteluo.todayclass.ui.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eggetteluo.todayclass.model.Course
import com.eggetteluo.todayclass.ui.components.CourseItem
import com.eggetteluo.todayclass.util.ExcelParser
import com.eggetteluo.todayclass.util.TimeUtil
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    val excelParser = remember { ExcelParser() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()

    // 状态管理
    var showWeekDialog by remember { mutableStateOf(false) }
    var tempCourseList by remember { mutableStateOf<List<Course>>(emptyList()) }
    var inputWeek by remember { mutableIntStateOf(1) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }

    val courses by viewModel.displayCourses.collectAsStateWithLifecycle()
    val currentWeek by viewModel.currentWeek.collectAsStateWithLifecycle()
    val isTomorrow by viewModel.showTomorrow.collectAsStateWithLifecycle()

    val liveTodayText by produceState(initialValue = TimeUtil.getTodayFullDateString()) {
        while (true) {
            delay(60_000)
            value = TimeUtil.getTodayFullDateString()
        }
    }

    val displayDateString = remember(isTomorrow, liveTodayText) {
        if (isTomorrow) {
            "明天课程"
        } else {
            liveTodayText
        }
    }

    val launcher = rememberFilePickerLauncher(
        type = PickerType.File(extensions = listOf("xlsx", "xls")),
        title = "选择课表 Excel"
    ) { file ->
        if (file != null) {
            scope.launch {
                try {
                    val bytes = file.readBytes()
                    val courseList = excelParser.parse(bytes)
                    tempCourseList = courseList
                    importErrorMessage = null
                    showWeekDialog = true
                } catch (e: Exception) {
                    Napier.e(tag = "IMPORT", throwable = e) { "解析失败" }
                    importErrorMessage = e.message ?: "导入失败，请检查文件格式后重试"
                }
            }
        }
    }

    if (importErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { importErrorMessage = null },
            title = { Text("导入失败") },
            text = { Text(importErrorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { importErrorMessage = null }) {
                    Text("我知道了")
                }
            }
        )
    }

    // --- 周次确认对话框 (M3 风格) ---
    if (showWeekDialog) {
        AlertDialog(
            onDismissRequest = { showWeekDialog = false },
            icon = { Icon(Icons.Outlined.EventNote, contentDescription = null) },
            title = { Text("初始化课表配置") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "导入成功！请确认今天是第几周？",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            FilledIconButton(
                                onClick = { if (inputWeek > 1) inputWeek-- },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) { Text("-", fontSize = 20.sp) }

                            Text(
                                text = "第 $inputWeek 周",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )

                            FilledIconButton(
                                onClick = { if (inputWeek < 25) inputWeek++ },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) { Text("+", fontSize = 20.sp) }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.importCoursesWithConfig(tempCourseList, inputWeek)
                    showWeekDialog = false
                }) { Text("开始使用") }
            },
            dismissButton = {
                TextButton(onClick = { showWeekDialog = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isTomorrow) "明日预告" else "今日课表",
                            fontWeight = FontWeight.ExtraBold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displayDateString,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = " · ",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.padding(start = 2.dp)
                            ) {
                                Text(
                                    text = "第 $currentWeek 周",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold
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
                    actionIconContentColor = Color.Unspecified
                ),
                actions = {
                    FilterChip(
                        selected = isTomorrow,
                        onClick = { viewModel.toggleTomorrow(!isTomorrow) },
                        label = { Text(if (isTomorrow) "返回今日" else "看明天") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isTomorrow) Icons.Default.Schedule else Icons.AutoMirrored.Filled.EventNote,
                                modifier = Modifier.size(18.dp),
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { launcher.launch() },
                icon = { Icon(Icons.Default.Add, "Import") },
                text = { Text("导入课表") },
                expanded = listState.firstVisibleItemIndex == 0, // 滚动时自动折叠 FAB
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (courses.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.EventNote,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (isTomorrow) "明天没课，今晚奖励一把王者荣耀？" else "今日无课，勾栏听曲",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 80.dp), // 为 FAB 留出空间
                    verticalArrangement = Arrangement.spacedBy(4.dp) // 配合 CourseItem 内部 padding
                ) {
                    items(
                        items = courses,
                        key = { course ->
                            if (course.id != 0L) course.id
                            else "${course.name}-${course.dayOfWeek}-${course.section}-${course.originalWeeks}"
                        }
                    ) { course ->
                        CourseItem(course, !isTomorrow)
                    }
                }
            }
        }
    }
}
