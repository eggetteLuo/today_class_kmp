package com.eggetteluo.todayclass.core.database.mapper

import com.eggetteluo.todayclass.core.database.entity.CourseScheduleEntity
import com.eggetteluo.todayclass.core.excel.model.CourseScheduleInstance

fun CourseScheduleInstance.toEntity(): CourseScheduleEntity {
    return CourseScheduleEntity(
        courseName = courseName,
        courseCode = courseCode,
        teacher = teacher,
        dayOfWeek = dayOfWeek,
        startPeriod = startPeriod,
        periodCount = periodCount,
        week = week,
        location = location,
    )
}
