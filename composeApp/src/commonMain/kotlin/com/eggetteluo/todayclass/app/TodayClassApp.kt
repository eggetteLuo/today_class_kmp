package com.eggetteluo.todayclass.app

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import com.eggetteluo.todayclass.navigation.TodayClassNavHost

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun TodayClassApp() {
    MaterialExpressiveTheme {
        TodayClassNavHost()
    }
}
