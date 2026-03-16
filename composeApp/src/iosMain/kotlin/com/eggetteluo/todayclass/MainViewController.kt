package com.eggetteluo.todayclass

import androidx.compose.ui.window.ComposeUIViewController
import com.eggetteluo.todayclass.di.initKoin
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

fun MainViewController() = ComposeUIViewController(
    configure = {
        initKoin()
    }
) {
    // Logger
    Napier.base(DebugAntilog())
    App()
}