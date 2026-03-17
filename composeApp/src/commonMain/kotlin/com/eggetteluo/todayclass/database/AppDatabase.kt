package com.eggetteluo.todayclass.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.eggetteluo.todayclass.model.Course

@Database(entities = [Course::class], version = 1)
@TypeConverters(CourseConverters::class)
abstract class AppDatabase : RoomDatabase(), DB {
    abstract override fun courseDao(): CourseDao
}

// 定义一个简单的接口，方便 Koin 注入
interface DB {
    fun courseDao(): CourseDao
}

/**
 * 跨平台构建器工厂
 * @param ctx 在 Android 端传入 Context，在其他平台传入 null 或对应环境对象
 */
expect fun getDatabaseBuilder(ctx: Any?): RoomDatabase.Builder<AppDatabase>

/**
 * 通用的数据库初始化函数
 */
fun getDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder
        // 使用 BundledSQLiteDriver 保证跨平台一致性
        .setDriver(androidx.sqlite.driver.bundled.BundledSQLiteDriver())
        .fallbackToDestructiveMigration(true) // 开发阶段改模型会自动重建数据库
        .build()
}