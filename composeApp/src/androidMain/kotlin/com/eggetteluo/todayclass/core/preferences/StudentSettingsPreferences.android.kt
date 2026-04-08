package com.eggetteluo.todayclass.core.preferences

import android.content.Context

private const val PREF_NAME = "today_class_prefs"
private const val KEY_SHOW_IMPORT_BUTTON = "show_import_button"

actual object StudentSettingsPreferences {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual fun getShowImportButton(): Boolean {
        val context = appContext ?: return true
        return context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_IMPORT_BUTTON, true)
    }

    actual fun setShowImportButton(value: Boolean) {
        val context = appContext ?: return
        context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_IMPORT_BUTTON, value)
            .apply()
    }
}
