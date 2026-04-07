package com.eggetteluo.todayclass.core.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.Foundation.NSNotificationCenter

@Composable
actual fun FullscreenLandscapeEffect(enabled: Boolean) {
    DisposableEffect(enabled) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            if (enabled) "TodayClassFullscreenEnabled" else "TodayClassFullscreenDisabled",
            null,
        )
        onDispose {
            NSNotificationCenter.defaultCenter.postNotificationName(
                "TodayClassFullscreenDisabled",
                null,
            )
        }
    }
}
