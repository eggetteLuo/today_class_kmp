package com.eggetteluo.todayclass.util

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

object TimeUtil {
    // 获取今天是星期几 (1-7)
    fun getTodayDayOfWeek(): Int {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return now.dayOfWeek.isoDayNumber
    }

    /**
     * 根据用户输入的当前周次，反推学期开始那一周的周一日期
     * @param currentWeek 用户输入的当前周 (例如: 3)
     */
    fun calculateSemesterStart(currentWeek: Int): LocalDate {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        // 1. 先找到本周的周一
        val daysFromMonday = now.dayOfWeek.isoDayNumber - 1
        val thisMonday = now.minus(DatePeriod(days = daysFromMonday))

        // 2. 再向上推 (currentWeek - 1) 周即为第一周的周一
        return thisMonday.minus(DatePeriod(days = (currentWeek - 1) * 7))
    }

    /**
     * 根据学期开始日期计算当前是第几周
     */
    fun calculateCurrentWeek(startDate: LocalDate): Int {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val daysBetween = startDate.daysUntil(now)

        // 如果还没开学，返回1；否则向下取整周数 + 1
        return if (daysBetween < 0) 1 else (daysBetween / 7) + 1
    }

    /**
     * 获取格式化的今天日期，例如 "3月18日 星期三"
     */
    fun getTodayFullDateString(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val month = now.month.number
        val day = now.day
        val dayOfWeekChinese = when (now.dayOfWeek.isoDayNumber) {
            1 -> "星期一"
            2 -> "星期二"
            3 -> "星期三"
            4 -> "星期四"
            5 -> "星期五"
            6 -> "星期六"
            7 -> "星期日"
            else -> ""
        }
        return "${month}月${day}日 $dayOfWeekChinese"
    }

}