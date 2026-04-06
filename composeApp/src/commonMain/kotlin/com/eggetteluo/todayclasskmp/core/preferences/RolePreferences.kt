package com.eggetteluo.todayclasskmp.core.preferences

expect object RolePreferences {
    fun getSelectedRoleCode(): String?
    fun setSelectedRoleCode(roleCode: String)
}
