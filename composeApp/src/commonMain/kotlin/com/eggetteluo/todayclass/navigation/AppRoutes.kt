package com.eggetteluo.todayclass.navigation

object AppRoutes {
    const val RoleSelect = "role_select"
    const val DesktopPattern = "desktop/{role}"

    fun desktop(roleCode: String): String = "desktop/$roleCode"
}
