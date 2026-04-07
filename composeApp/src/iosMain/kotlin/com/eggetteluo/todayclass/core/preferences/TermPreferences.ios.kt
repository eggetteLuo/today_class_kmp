package com.eggetteluo.todayclass.core.preferences

import platform.Foundation.NSUserDefaults

private const val KEY_TERM_START_EPOCH_DAY = "term_start_epoch_day"
private const val KEY_LAST_SELECTED_WEEK = "last_selected_week"

actual object TermPreferences {
    private val userDefaults: NSUserDefaults by lazy { NSUserDefaults.standardUserDefaults }

    actual fun getTermStartEpochDay(): Long? {
        return userDefaults.stringForKey(KEY_TERM_START_EPOCH_DAY)?.toLongOrNull()
    }

    actual fun setTermStartEpochDay(epochDay: Long) {
        userDefaults.setObject(epochDay.toString(), forKey = KEY_TERM_START_EPOCH_DAY)
    }

    actual fun getLastSelectedWeek(): Int? {
        return if (userDefaults.objectForKey(KEY_LAST_SELECTED_WEEK) != null) {
            userDefaults.integerForKey(KEY_LAST_SELECTED_WEEK).toInt()
        } else {
            null
        }
    }

    actual fun setLastSelectedWeek(week: Int) {
        userDefaults.setObject(week, forKey = KEY_LAST_SELECTED_WEEK)
    }
}
