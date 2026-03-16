package com.eggetteluo.todayclass.navigation

import kotlinx.serialization.Serializable

sealed interface Routes {

    // 首页
    @Serializable
    object Home : Routes

}