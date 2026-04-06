package com.eggetteluo.todayclasskmp.core.preferences

import android.content.Context

private const val PREF_NAME = "today_class_prefs"
private const val KEY_ROLE_CODE = "selected_role_code"

actual object RolePreferences {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual fun getSelectedRoleCode(): String? {
        val context = appContext ?: return null
        return context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ROLE_CODE, null)
    }

    actual fun setSelectedRoleCode(roleCode: String) {
        val context = appContext ?: return
        context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ROLE_CODE, roleCode)
            .apply()
    }
}
