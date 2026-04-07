package com.eggetteluo.todayclass.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.eggetteluo.todayclass.core.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

private const val DB_NAME = "today_class.db"

actual fun platformModule() = module {
    single<AppDatabase> {
        Room.databaseBuilder<AppDatabase>(
            name = documentDirectory() + "/$DB_NAME",
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    }
    single { get<AppDatabase>().courseScheduleDao() }
}

private fun documentDirectory(): String {
    val paths = NSFileManager.defaultManager.URLsForDirectory(
        directory = NSDocumentDirectory,
        inDomains = NSUserDomainMask,
    )
    val lastPath = paths.lastOrNull() as? NSURL
    return requireNotNull(lastPath?.path) { "Cannot resolve iOS document directory" }
}
