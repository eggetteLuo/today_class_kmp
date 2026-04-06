package com.eggetteluo.todayclasskmp.core.preferences

expect object TermPreferences {
    fun getTermStartEpochDay(): Long?
    fun setTermStartEpochDay(epochDay: Long)
    fun getLastSelectedWeek(): Int?
    fun setLastSelectedWeek(week: Int)
}
