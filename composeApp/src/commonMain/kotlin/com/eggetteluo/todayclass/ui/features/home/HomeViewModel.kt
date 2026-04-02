package com.eggetteluo.todayclass.ui.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eggetteluo.todayclass.data.SettingsRepository
import com.eggetteluo.todayclass.database.CourseDao
import com.eggetteluo.todayclass.model.Course
import com.eggetteluo.todayclass.util.TimeUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class HomeViewModel(
    private val courseDao: CourseDao,
    private val settingsRepository: SettingsRepository // 注入存储类
) : ViewModel() {

    // 控制显示今天还是明天
    private val _showTomorrow = MutableStateFlow(false)
    val showTomorrow: StateFlow<Boolean> = _showTomorrow.asStateFlow()

    // 每分钟触发一次，用于跨天后自动刷新“今天是周几/第几周”
    private val refreshTicker: Flow<Unit> = flow {
        emit(Unit)
        while (true) {
            delay(60_000)
            emit(Unit)
        }
    }

    private val todayDay: StateFlow<Int> = refreshTicker
        .map { TimeUtil.getTodayDayOfWeek() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TimeUtil.getTodayDayOfWeek())

    // 自动计算的当前周次 (Flow)
    val currentWeek: StateFlow<Int> = combine(
        settingsRepository.startDateFlow,
        refreshTicker
    ) { dateStr, _ ->
        if (dateStr == null) 1
        else TimeUtil.calculateCurrentWeek(LocalDate.parse(dateStr))
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val displayCourses: StateFlow<List<Course>> = combine(
        _showTomorrow,
        todayDay,
        currentWeek
    ) { isTomorrow, today, week ->
        // 计算目标天：如果是明天，且今天是周日(7)，则目标是下周一(1)
        val targetDay = if (isTomorrow) (if (today == 7) 1 else today + 1) else today
        val targetWeek = if (isTomorrow && today == 7) week + 1 else week

        Triple(targetDay, targetWeek, isTomorrow)
    }.flatMapLatest { (day, week, _) ->
        courseDao.getCoursesByDay(day).map { courses ->
            courses.filter { it.weekList.contains(week) }
                .sortedBy { it.section }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /**
     * 核心：用户导入课表并告知今天是第几周
     */
    fun importCoursesWithConfig(courses: List<Course>, inputWeek: Int) {
        viewModelScope.launch {
            // 1. 保存课程
            courseDao.clearAll()
            courseDao.insertAll(courses)

            // 2. 根据用户输入的周次，计算学期开始日期并存储
            val startDate = TimeUtil.calculateSemesterStart(inputWeek)
            settingsRepository.saveStartDate(startDate.toString())

            // 3. UI 会通过 currentWeek 这个 Flow 自动更新
        }
    }

    fun toggleTomorrow(show: Boolean) {
        _showTomorrow.value = show
    }
}
