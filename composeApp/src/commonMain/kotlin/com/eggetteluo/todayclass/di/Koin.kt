package com.eggetteluo.todayclass.di

import com.eggetteluo.todayclass.database.AppDatabase
import com.eggetteluo.todayclass.database.getDatabase
import com.eggetteluo.todayclass.ui.features.home.HomeViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val commonModule = module {
    // 1. 获取平台已经提供好的 Builder，然后创建数据库
    single<AppDatabase> {
        getDatabase(get()) // 这里的 get() 会寻找 RoomDatabase.Builder<AppDatabase>
    }

    // 2. 注入 DAO
    single { get<AppDatabase>().courseDao() }

    // 3. 注入 ViewModel
    viewModelOf(::HomeViewModel)
}

expect val platformModule: Module

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(commonModule, platformModule)
    }
}