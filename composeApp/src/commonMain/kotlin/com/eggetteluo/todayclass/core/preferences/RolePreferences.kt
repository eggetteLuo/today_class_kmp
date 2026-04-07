package com.eggetteluo.todayclass.core.preferences

expect object RolePreferences {
    fun getSelectedRoleCode(): String?
    fun setSelectedRoleCode(roleCode: String)
}
