package com.eggetteluo.todayclasskmp.core.preferences

import platform.Foundation.NSUserDefaults

private const val KEY_ROLE_CODE = "selected_role_code"

actual object RolePreferences {
    private val userDefaults: NSUserDefaults by lazy { NSUserDefaults.standardUserDefaults }

    actual fun getSelectedRoleCode(): String? {
        return userDefaults.stringForKey(KEY_ROLE_CODE)
    }

    actual fun setSelectedRoleCode(roleCode: String) {
        userDefaults.setObject(roleCode, forKey = KEY_ROLE_CODE)
    }
}
