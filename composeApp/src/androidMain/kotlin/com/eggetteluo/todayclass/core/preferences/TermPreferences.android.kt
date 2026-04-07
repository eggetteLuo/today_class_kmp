package com.eggetteluo.todayclass.core.preferences

import android.content.Context

private const val PREF_NAME = "today_class_prefs"
private const val KEY_TERM_START_EPOCH_DAY = "term_start_epoch_day"
private const val KEY_LAST_SELECTED_WEEK = "last_selected_week"

actual object TermPreferences {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual fun getTermStartEpochDay(): Long? {
        val context = appContext ?: return null
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_TERM_START_EPOCH_DAY)) {
            prefs.getLong(KEY_TERM_START_EPOCH_DAY, 0L)
        } else {
            null
        }
    }

    actual fun setTermStartEpochDay(epochDay: Long) {
        val context = appContext ?: return
        context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_TERM_START_EPOCH_DAY, epochDay)
            .apply()
    }

    actual fun getLastSelectedWeek(): Int? {
        val context = appContext ?: return null
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_LAST_SELECTED_WEEK)) {
            prefs.getInt(KEY_LAST_SELECTED_WEEK, 1)
        } else {
            null
        }
    }

    actual fun setLastSelectedWeek(week: Int) {
        val context = appContext ?: return
        context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAST_SELECTED_WEEK, week)
            .apply()
    }
}
