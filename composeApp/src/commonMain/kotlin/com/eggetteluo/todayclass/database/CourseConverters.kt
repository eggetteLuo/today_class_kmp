package com.eggetteluo.todayclass.database

import androidx.room.TypeConverter

class CourseConverters {

    @TypeConverter
    fun fromWeekList(value: List<Int>): String = value.joinToString(",")

    @TypeConverter
    fun toWeekList(value: String): List<Int> = if (value.isEmpty())
        emptyList()
    else
        value.split(",").map { it.toInt() }

}