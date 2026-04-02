package com.eggetteluo.todayclass.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.eggetteluo.todayclass.model.Course

@Database(entities = [Course::class], version = 1)
@TypeConverters(CourseConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

expect fun getDatabaseBuilder(ctx: Any?): RoomDatabase.Builder<AppDatabase>

fun getDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder
        .setDriver(androidx.sqlite.driver.bundled.BundledSQLiteDriver())
        .fallbackToDestructiveMigration(true)
        .build()
}
