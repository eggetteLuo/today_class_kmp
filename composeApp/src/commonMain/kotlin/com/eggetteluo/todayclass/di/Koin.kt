package com.eggetteluo.todayclass.di

import com.eggetteluo.todayclass.data.SettingsRepository
import com.eggetteluo.todayclass.database.AppDatabase
import com.eggetteluo.todayclass.database.getDatabase
import com.eggetteluo.todayclass.ui.features.home.HomeViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val commonModule = module {
    single<AppDatabase> {
        getDatabase(get())
    }

    single { get<AppDatabase>().courseDao() }

    viewModelOf(::HomeViewModel)

    single { SettingsRepository(get()) }
}

expect val platformModule: Module

private var koinStarted = false

fun initKoin(config: KoinAppDeclaration? = null) {
    if (koinStarted) return
    koinStarted = true

    startKoin {
        config?.invoke(this)
        modules(commonModule, platformModule)
    }
}
