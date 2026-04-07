package com.eggetteluo.todayclass.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eggetteluo.todayclass.core.database.entity.CourseScheduleEntity

@Dao
interface CourseScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CourseScheduleEntity>)

    @Query("SELECT * FROM course_schedule ORDER BY week, dayOfWeek, startPeriod")
    suspend fun getAll(): List<CourseScheduleEntity>

    @Query(
        """
        SELECT * FROM course_schedule
        WHERE week = :week
        ORDER BY dayOfWeek, startPeriod
        """,
    )
    suspend fun getByWeek(week: Int): List<CourseScheduleEntity>

    @Query(
        """
        SELECT * FROM course_schedule
        WHERE week = :week AND dayOfWeek = :dayOfWeek
        ORDER BY startPeriod
        """,
    )
    suspend fun getByWeekAndDay(week: Int, dayOfWeek: Int): List<CourseScheduleEntity>

    @Query("DELETE FROM course_schedule")
    suspend fun clearAll()
}
