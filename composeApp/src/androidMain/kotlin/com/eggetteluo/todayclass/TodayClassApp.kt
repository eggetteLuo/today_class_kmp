package com.eggetteluo.todayclass

import android.app.Application
import com.eggetteluo.todayclass.di.initKoin
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class TodayClassApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Logger
        Napier.base(DebugAntilog())

        initKoin {
            androidLogger()
            androidContext(this@TodayClassApp)
        }
    }

}