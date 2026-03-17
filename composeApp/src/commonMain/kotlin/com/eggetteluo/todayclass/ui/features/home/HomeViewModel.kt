package com.eggetteluo.todayclass.ui.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eggetteluo.todayclass.database.CourseDao
import com.eggetteluo.todayclass.model.Course
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val courseDao: CourseDao) : ViewModel() {

    private val _currentWeek = MutableStateFlow(3)  // 第三周
    private val _currentDay = MutableStateFlow(2)   // 周三

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayCourses: StateFlow<List<Course>> = combine(
        _currentDay.flatMapLatest { day -> courseDao.getCoursesByDay(day) },
        _currentWeek
    ) { courses, week ->
        courses.filter { it.weekList.contains(week) }
            .sortedBy { it.section } // 按节次排序
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun importCourses(courses: List<Course>) {
        viewModelScope.launch {
            courseDao.clearAll()
            courseDao.insertAll(courses)
        }
    }

}