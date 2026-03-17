package com.eggetteluo.todayclass.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

actual fun getDatabaseBuilder(ctx: Any?): RoomDatabase.Builder<AppDatabase> {
    // 将传入的对象转换为 Android Context
    val appContext = (ctx as? Context) ?: throw IllegalArgumentException("Android Room requires a Context")

    // 获取 App 的私有数据库路径
    val dbFile = appContext.getDatabasePath("today_class.db")

    return Room.databaseBuilder<AppDatabase>(
        context = appContext,
        name = dbFile.absolutePath
    )
}