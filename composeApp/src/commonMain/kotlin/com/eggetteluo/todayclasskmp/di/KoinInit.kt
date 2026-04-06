package com.eggetteluo.todayclasskmp.di

import com.eggetteluo.todayclasskmp.core.database.repository.CourseScheduleRepository
import com.eggetteluo.todayclasskmp.core.service.NavigationTextProvider
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

private val appModule = module {
    single { NavigationTextProvider() }
    single { CourseScheduleRepository(get()) }
}

expect fun platformModule(): Module

fun initKoin(appDeclaration: KoinApplication.() -> Unit = {}) {
    if (KoinPlatform.getKoinOrNull() == null) {
        startKoin {
            appDeclaration()
            modules(appModule)
            modules(platformModule())
        }
    }
}
