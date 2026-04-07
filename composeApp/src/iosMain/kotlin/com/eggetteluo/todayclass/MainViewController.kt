package com.eggetteluo.todayclass

import androidx.compose.ui.window.ComposeUIViewController
import com.eggetteluo.todayclass.di.initKoin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initKoin()
    return ComposeUIViewController { App() }
}
