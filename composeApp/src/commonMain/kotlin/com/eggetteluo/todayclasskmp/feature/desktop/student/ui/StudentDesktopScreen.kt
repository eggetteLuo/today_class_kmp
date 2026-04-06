package com.eggetteluo.todayclasskmp.feature.desktop.student.ui

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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eggetteluo.todayclasskmp.core.database.entity.CourseScheduleEntity
import com.eggetteluo.todayclasskmp.core.database.mapper.toEntity
import com.eggetteluo.todayclasskmp.core.database.repository.CourseScheduleRepository
import com.eggetteluo.todayclasskmp.core.excel.ExcelDebugReader
import com.eggetteluo.todayclasskmp.core.log.AppLogger
import com.eggetteluo.todayclasskmp.core.preferences.TermPreferences
import com.eggetteluo.todayclasskmp.core.system.FullscreenLandscapeEffect
import com.eggetteluo.todayclasskmp.core.time.AcademicWeekCalculator
import com.eggetteluo.todayclasskmp.feature.desktop.components.RoleDesktopScaffold
import com.eggetteluo.todayclasskmp.feature.desktop.model.DesktopTab
import com.eggetteluo.todayclasskmp.feature.desktop.student.ui.components.CourseItemCard
import com.eggetteluo.todayclasskmp.feature.desktop.student.ui.components.WeekPickerDialog
import com.eggetteluo.todayclasskmp.feature.desktop.student.ui.components.WeekTimetableBoard
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import org.koin.compose.koinInject
import kotlin.time.Clock

private val studentTabs = listOf(
    DesktopTab(topTitle = "看今日", bottomLabel = "看今日", icon = Icons.Outlined.Today),
    DesktopTab(topTitle = "周课表", bottomLabel = "周课表", icon = Icons.Outlined.ViewWeek),
    DesktopTab(topTitle = "设置", bottomLabel = "设置", icon = Icons.Outlined.Settings),
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun StudentDesktopScreen() {
    val scope = rememberCoroutineScope()
    val repository = koinInject<CourseScheduleRepository>()

    var showWeekDialog by rememberSaveable { mutableStateOf(false) }
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    var inputWeek by rememberSaveable { mutableStateOf((TermPreferences.getLastSelectedWeek() ?: 1).coerceIn(1, 25)) }
    var pendingSelectedWeek by rememberSaveable { mutableStateOf<Int?>(null) }
    var refreshKey by rememberSaveable { mutableStateOf(0) }
    var isTomorrow by rememberSaveable { mutableStateOf(false) }
    var isWeekFullScreen by rememberSaveable { mutableStateOf(false) }
    var weekView by rememberSaveable {
        mutableStateOf(
            TermPreferences.getTermStartEpochDay()?.let { AcademicWeekCalculator.calculateCurrentWeek(it) } ?: 1,
        )
    }
    FullscreenLandscapeEffect(enabled = isWeekFullScreen)
    val timeTick by produceState(initialValue = 0L) {
        while (true) {
            value = Clock.System.now().toEpochMilliseconds()
            delay(30_000L)
        }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val todayUiState by produceState(
        initialValue = TodayCoursesUiState(),
        key1 = refreshKey,
        key2 = isTomorrow,
    ) {
        val termStartEpochDay = TermPreferences.getTermStartEpochDay()
        if (termStartEpochDay == null) {
            value = TodayCoursesUiState(termConfigured = false)
            return@produceState
        }

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val offsetDays = if (isTomorrow) 1 else 0
        val targetDate = LocalDate.fromEpochDays(today.toEpochDays() + offsetDays)
        val targetWeek = (((targetDate.toEpochDays() - termStartEpochDay) / 7L).toInt() + 1).coerceAtLeast(1)
        val targetDayOfWeek = targetDate.dayOfWeek.ordinal + 1
        val courses = repository.getByWeekAndDay(targetWeek, targetDayOfWeek)
        value = TodayCoursesUiState(
            termConfigured = true,
            isTomorrow = isTomorrow,
            currentWeek = targetWeek,
            displayDateString = formatDisplayDate(targetDate),
            courses = courses,
        )
    }

    val weekUiState by produceState(
        initialValue = WeekCoursesUiState(),
        key1 = refreshKey,
        key2 = weekView,
    ) {
        val termStartEpochDay = TermPreferences.getTermStartEpochDay()
        if (termStartEpochDay == null) {
            value = WeekCoursesUiState(termConfigured = false)
            return@produceState
        }
        val currentWeek = AcademicWeekCalculator.calculateCurrentWeek(termStartEpochDay)
        val targetWeek = weekView.coerceAtLeast(1)
        val courses = repository.getByWeek(targetWeek)
        value = WeekCoursesUiState(
            termConfigured = true,
            currentWeek = currentWeek,
            displayingWeek = targetWeek,
            courses = courses,
        )
    }

    val excelPickerLauncher = rememberFilePickerLauncher(
        type = PickerType.File(extensions = listOf("xlsx", "xls")),
        title = "选择课表文件",
    ) { pickedFile ->
        val selectedWeek = pendingSelectedWeek
        if (pickedFile != null && selectedWeek != null) {
            scope.launch {
                val instances = ExcelDebugReader.readAndLog(pickedFile)
                AppLogger.i("StudentDesktop", "Excel instances parsed: ${instances.size}")
                repository.replaceAll(instances.map { it.toEntity() })
                AppLogger.i("StudentDesktop", "Saved instances into Room: ${instances.size}")

                val termStartEpochDay = AcademicWeekCalculator.deriveTermStartEpochDayFromToday(selectedWeek)
                TermPreferences.setTermStartEpochDay(termStartEpochDay)
                TermPreferences.setLastSelectedWeek(selectedWeek)
                weekView = selectedWeek
                refreshKey++
            }
        }
        pendingSelectedWeek = null
    }

    RoleDesktopScaffold(
        roleLabel = "学生",
        tabs = studentTabs,
        topBarContainerColor = MaterialTheme.colorScheme.primaryContainer,
        topBarContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = { selectedTabIndex = it },
        scaffoldModifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { selectedTabIndex, currentTab ->
            when (selectedTabIndex) {
                0 -> TodayTopBar(todayUiState = todayUiState, isTomorrow = isTomorrow, onToggleTomorrow = { isTomorrow = !isTomorrow }, scrollBehavior = scrollBehavior)
                1 -> WeekTopBar(
                    weekUiState = weekUiState,
                    scrollBehavior = scrollBehavior,
                    onPrevWeek = { if (weekView > 1) weekView -= 1 },
                    onNextWeek = { weekView += 1 },
                    onBackToCurrentWeek = { weekView = weekUiState.currentWeek ?: weekView },
                    onFullScreen = { isWeekFullScreen = true },
                )
                else -> TopAppBar(title = { Text(currentTab.topTitle) })
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showWeekDialog = true },
                icon = { Icon(imageVector = Icons.Outlined.Add, contentDescription = null) },
                text = { Text("导入课表") },
            )
        },
        content = { selectedTabIndex, _ ->
            when (selectedTabIndex) {
                0 -> TodayCoursesContent(state = todayUiState, timeTick = timeTick)
                1 -> WeekCoursesContent(state = weekUiState)
                else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("该页面开发中") }
            }
        },
    )

    WeekPickerDialog(
        show = showWeekDialog,
        currentWeek = inputWeek,
        onWeekChange = { inputWeek = it },
        onConfirm = {
            pendingSelectedWeek = inputWeek
            showWeekDialog = false
            scope.launch {
                delay(180)
                excelPickerLauncher.launch()
            }
        },
        onDismiss = { showWeekDialog = false },
    )

    if (isWeekFullScreen) {
        WeekTimetableFullScreen(
            state = weekUiState,
            onDismiss = { isWeekFullScreen = false },
            onPrevWeek = { if (weekView > 1) weekView -= 1 },
            onNextWeek = { weekView += 1 },
            onBackToCurrentWeek = { weekView = weekUiState.currentWeek ?: weekView },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TodayTopBar(
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
@OptIn(ExperimentalMaterial3Api::class)
private fun WeekTopBar(
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
private fun TodayCoursesContent(state: TodayCoursesUiState, timeTick: Long) {
    if (!state.termConfigured) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("请先点击右下角“导入课表”并设置当前周次")
        }
        return
    }
    if (state.courses.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("第 ${state.currentWeek ?: "-"} 周${if (state.isTomorrow) "明天" else "今天"}暂无课程")
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
private fun WeekCoursesContent(state: WeekCoursesUiState) {
    if (!state.termConfigured) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("请先导入课表并设置当前周次")
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
            ) { Text("第 ${state.displayingWeek} 周暂无课程") }
        } else {
            WeekTimetableBoard(courses = state.courses, modifier = Modifier.fillMaxWidth())
        }
        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun WeekTimetableFullScreen(
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
                ) { Text("第 ${state.displayingWeek} 周暂无课程") }
            } else {
                WeekTimetableBoard(courses = state.courses, modifier = Modifier.fillMaxWidth())
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private data class TodayCoursesUiState(
    val termConfigured: Boolean = false,
    val isTomorrow: Boolean = false,
    val currentWeek: Int? = null,
    val displayDateString: String = "",
    val courses: List<CourseScheduleEntity> = emptyList(),
)

private data class WeekCoursesUiState(
    val termConfigured: Boolean = false,
    val currentWeek: Int? = null,
    val displayingWeek: Int = 1,
    val courses: List<CourseScheduleEntity> = emptyList(),
)

private fun formatDisplayDate(date: LocalDate): String {
    val week = when (date.dayOfWeek.ordinal + 1) {
        1 -> "星期一"
        2 -> "星期二"
        3 -> "星期三"
        4 -> "星期四"
        5 -> "星期五"
        6 -> "星期六"
        else -> "星期日"
    }
    return "${date.month.number}月${date.day}日 $week"
}
