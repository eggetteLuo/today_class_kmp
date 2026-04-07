package com.eggetteluo.todayclass.feature.desktop.student.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.eggetteluo.todayclass.core.database.entity.CourseScheduleEntity
import com.eggetteluo.todayclass.core.database.mapper.toEntity
import com.eggetteluo.todayclass.core.database.repository.CourseScheduleRepository
import com.eggetteluo.todayclass.core.excel.ExcelDebugReader
import com.eggetteluo.todayclass.core.log.AppLogger
import com.eggetteluo.todayclass.core.preferences.TermPreferences
import com.eggetteluo.todayclass.core.system.FullscreenLandscapeEffect
import com.eggetteluo.todayclass.core.time.AcademicWeekCalculator
import com.eggetteluo.todayclass.feature.desktop.components.RoleDesktopScaffold
import com.eggetteluo.todayclass.feature.desktop.model.DesktopTab
import com.eggetteluo.todayclass.feature.desktop.student.ui.components.TodayCoursesContent
import com.eggetteluo.todayclass.feature.desktop.student.ui.components.TodayTopBar
import com.eggetteluo.todayclass.feature.desktop.student.ui.components.WeekCoursesContent
import com.eggetteluo.todayclass.feature.desktop.student.ui.components.WeekPickerDialog
import com.eggetteluo.todayclass.feature.desktop.student.ui.components.WeekTimetableFullScreen
import com.eggetteluo.todayclass.feature.desktop.student.ui.components.WeekTopBar
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
    var isImportFlowActive by rememberSaveable { mutableStateOf(false) }
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
        if (!isImportFlowActive) {
            return@rememberFilePickerLauncher
        }
        val selectedWeek = pendingSelectedWeek
        scope.launch {
            try {
                if (pickedFile != null && selectedWeek != null) {
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
            } finally {
                pendingSelectedWeek = null
                isImportFlowActive = false
            }
        }
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
            if (isImportFlowActive) return@WeekPickerDialog
            pendingSelectedWeek = inputWeek
            isImportFlowActive = true
            showWeekDialog = false
            scope.launch {
                delay(180)
                runCatching { excelPickerLauncher.launch() }
                    .onFailure {
                        AppLogger.e("StudentDesktop", "Failed to launch excel picker", it)
                        pendingSelectedWeek = null
                        isImportFlowActive = false
                    }
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

internal data class TodayCoursesUiState(
    val termConfigured: Boolean = false,
    val isTomorrow: Boolean = false,
    val currentWeek: Int? = null,
    val displayDateString: String = "",
    val courses: List<CourseScheduleEntity> = emptyList(),
)

internal data class WeekCoursesUiState(
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
