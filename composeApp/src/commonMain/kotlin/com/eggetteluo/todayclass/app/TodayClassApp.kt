package com.eggetteluo.todayclass.app

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import com.eggetteluo.todayclass.core.theme.AppThemeState
import com.eggetteluo.todayclass.core.theme.createAppColorScheme
import com.eggetteluo.todayclass.navigation.TodayClassNavHost

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun TodayClassApp() {
    val accent = AppThemeState.currentAccent
    MaterialExpressiveTheme(
        colorScheme = createAppColorScheme(accent),
    ) {
        TodayClassNavHost()
    }
}
