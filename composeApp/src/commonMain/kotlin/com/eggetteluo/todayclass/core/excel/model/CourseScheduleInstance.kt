package com.eggetteluo.todayclass.core.excel.model

data class CourseScheduleInstance(
    val courseName: String,
    val courseCode: String,
    val teacher: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val periodCount: Int,
    val week: Int,
    val location: String,
)
