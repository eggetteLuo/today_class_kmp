package com.eggetteluo.todayclass

import android.app.Application
import com.eggetteluo.todayclass.core.preferences.RolePreferences
import com.eggetteluo.todayclass.core.preferences.StudentSettingsPreferences
import com.eggetteluo.todayclass.core.preferences.TermPreferences
import com.eggetteluo.todayclass.di.initKoin
import org.koin.android.ext.koin.androidContext

class TodayClassApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RolePreferences.init(this)
        TermPreferences.init(this)
        StudentSettingsPreferences.init(this)
        initKoin {
            androidContext(this@TodayClassApplication)
        }
    }
}
