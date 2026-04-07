package com.eggetteluo.todayclass.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "course_schedule",
    indices = [
        Index(value = ["week"]),
        Index(value = ["dayOfWeek", "startPeriod"]),
    ],
)
data class CourseScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val courseName: String,
    val courseCode: String,
    val teacher: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val periodCount: Int,
    val week: Int,
    val location: String,
)
