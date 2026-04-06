package com.eggetteluo.todayclasskmp

import androidx.compose.ui.window.ComposeUIViewController
import com.eggetteluo.todayclasskmp.di.initKoin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initKoin()
    return ComposeUIViewController { App() }
}
