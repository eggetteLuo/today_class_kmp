package com.eggetteluo.todayclasskmp.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.eggetteluo.todayclasskmp.core.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module

private const val DB_NAME = "today_class.db"

actual fun platformModule() = module {
    single<AppDatabase> {
        val context: Context = get()
        val dbFile = context.getDatabasePath(DB_NAME)
        Room.databaseBuilder<AppDatabase>(
            context = context,
            name = dbFile.absolutePath,
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
    single { get<AppDatabase>().courseScheduleDao() }
}
