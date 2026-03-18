package com.eggetteluo.todayclass.di

import android.content.Context
import androidx.room.RoomDatabase
import com.eggetteluo.todayclass.database.AppDatabase
import com.eggetteluo.todayclass.database.createDataStore
import com.eggetteluo.todayclass.database.getDatabaseBuilder
import org.koin.dsl.module

actual val platformModule = module {
    single<RoomDatabase.Builder<AppDatabase>> {
        getDatabaseBuilder(get<Context>())
    }

    single { createDataStore(get()) }
}