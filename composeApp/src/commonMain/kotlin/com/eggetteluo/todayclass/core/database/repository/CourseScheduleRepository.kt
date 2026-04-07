package com.eggetteluo.todayclass.core.database.repository

import com.eggetteluo.todayclass.core.database.dao.CourseScheduleDao
import com.eggetteluo.todayclass.core.database.entity.CourseScheduleEntity

class CourseScheduleRepository(
    private val dao: CourseScheduleDao,
) {
    suspend fun replaceAll(items: List<CourseScheduleEntity>) {
        dao.clearAll()
        dao.insertAll(items)
    }

    suspend fun getAll(): List<CourseScheduleEntity> = dao.getAll()

    suspend fun getByWeek(week: Int): List<CourseScheduleEntity> = dao.getByWeek(week)

    suspend fun getByWeekAndDay(week: Int, dayOfWeek: Int): List<CourseScheduleEntity> {
        return dao.getByWeekAndDay(week, dayOfWeek)
    }
}
