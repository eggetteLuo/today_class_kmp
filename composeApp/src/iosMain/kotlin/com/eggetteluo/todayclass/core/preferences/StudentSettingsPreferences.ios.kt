package com.eggetteluo.todayclass.core.preferences

import platform.Foundation.NSUserDefaults

private const val KEY_SHOW_IMPORT_BUTTON = "show_import_button"

actual object StudentSettingsPreferences {
    private val userDefaults: NSUserDefaults by lazy { NSUserDefaults.standardUserDefaults }

    actual fun getShowImportButton(): Boolean {
        return if (userDefaults.objectForKey(KEY_SHOW_IMPORT_BUTTON) != null) {
            userDefaults.boolForKey(KEY_SHOW_IMPORT_BUTTON)
        } else {
            true
        }
    }

    actual fun setShowImportButton(value: Boolean) {
        userDefaults.setBool(value, forKey = KEY_SHOW_IMPORT_BUTTON)
    }
}
