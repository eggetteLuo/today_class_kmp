package com.eggetteluo.todayclass.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.eggetteluo.todayclass.model.Course
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Insert
    suspend fun insertAll(courses: List<Course>)

    @Query("DELETE FROM courses")
    suspend fun clearAll()

    // 首页只需要取当天的课
    @Query("SELECT * FROM courses WHERE dayOfWeek = :day")
    fun getCoursesByDay(day: Int): Flow<List<Course>>

    @Query("SELECT * FROM courses")
    fun getAllCourses(): Flow<List<Course>>
}