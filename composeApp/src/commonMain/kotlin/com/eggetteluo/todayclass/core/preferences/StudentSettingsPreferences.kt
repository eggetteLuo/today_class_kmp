package com.eggetteluo.todayclass.core.preferences

expect object StudentSettingsPreferences {
    fun getShowImportButton(): Boolean
    fun setShowImportButton(value: Boolean)
    fun getThemeAccent(): String?
    fun setThemeAccent(value: String)
}
