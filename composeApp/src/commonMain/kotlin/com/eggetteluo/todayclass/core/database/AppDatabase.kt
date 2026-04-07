package com.eggetteluo.todayclass.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.eggetteluo.todayclass.core.database.dao.CourseScheduleDao
import com.eggetteluo.todayclass.core.database.entity.CourseScheduleEntity

@Database(
    entities = [CourseScheduleEntity::class],
    version = 1,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseScheduleDao(): CourseScheduleDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
