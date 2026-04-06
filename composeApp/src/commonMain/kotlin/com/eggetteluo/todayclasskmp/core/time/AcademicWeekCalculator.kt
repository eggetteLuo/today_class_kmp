package com.eggetteluo.todayclasskmp.core.time

import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

object AcademicWeekCalculator {
    fun deriveTermStartEpochDayFromToday(currentWeek: Int): Long {
        require(currentWeek >= 1) { "currentWeek must be >= 1" }
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val mondayOffset = today.dayOfWeek.ordinal
        val todayEpochDay = today.toEpochDays()
        return todayEpochDay - mondayOffset - (currentWeek - 1) * 7L
    }

    fun calculateCurrentWeek(termStartEpochDay: Long): Int {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val currentMondayEpochDay = today.toEpochDays() - today.dayOfWeek.ordinal
        val passedWeeks = ((currentMondayEpochDay - termStartEpochDay) / 7L).toInt()
        return (passedWeeks + 1).coerceAtLeast(1)
    }

    fun todayDayOfWeekIso(): Int {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        return today.dayOfWeek.ordinal + 1
    }
}
