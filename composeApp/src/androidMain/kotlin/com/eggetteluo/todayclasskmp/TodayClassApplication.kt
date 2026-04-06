package com.eggetteluo.todayclasskmp

import android.app.Application
import com.eggetteluo.todayclasskmp.core.preferences.RolePreferences
import com.eggetteluo.todayclasskmp.core.preferences.TermPreferences
import com.eggetteluo.todayclasskmp.di.initKoin
import org.koin.android.ext.koin.androidContext

class TodayClassApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RolePreferences.init(this)
        TermPreferences.init(this)
        initKoin {
            androidContext(this@TodayClassApplication)
        }
    }
}
