package com.eggetteluo.todayclass

import androidx.compose.ui.window.ComposeUIViewController
import com.eggetteluo.todayclass.di.initKoin
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

private var napierInitialized = false

fun MainViewController() = ComposeUIViewController(
    configure = {
        initKoin()
        if (!napierInitialized) {
            Napier.base(DebugAntilog())
            napierInitialized = true
        }
    }
) {
    App()
}
