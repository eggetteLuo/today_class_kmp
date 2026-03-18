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

    /**
     * 根据节次和教室位置，自动匹配学校的单双号楼时间表
     * @param section 节次，如 "1-2"
     * @param location 教室，如 "香樟苑教 1 栋-101"
     */
    fun getFormattedTimeRange(section: String, location: String): String {
        // 1. 判断单双号楼 (根据图片备注：单数栋表1，双数栋表2)
        val buildingNumber = Regex("\\d+").find(location)?.value?.toIntOrNull() ?: 1
        val isEvenBuilding = buildingNumber % 2 == 0

        // 2. 解析节次 (把 "1-2" 拆成 [1, 2])
        val sectionParts = section.split("-")
            .mapNotNull { it.trim().toIntOrNull() }

        if (sectionParts.isEmpty()) return section

        val startSection = sectionParts.first() // 第一节
        val endSection = sectionParts.last()   // 最后一节

        // 3. 定义原始时间表数据 (对应图片中的每一行)
        val table1 = mapOf(
            1 to ("08:00" to "08:45"), 2 to ("08:55" to "09:40"),
            3 to ("10:25" to "11:10"), 4 to ("11:20" to "12:05"),
            5 to ("14:00" to "14:45"), 6 to ("14:55" to "15:40"),
            7 to ("16:25" to "17:10"), 8 to ("17:20" to "18:05"),
            9 to ("19:20" to "20:05"), 10 to ("20:15" to "21:00")
        )

        val table2 = mapOf(
            1 to ("08:20" to "09:05"), 2 to ("09:15" to "10:00"),
            3 to ("10:35" to "11:20"), 4 to ("11:30" to "12:15"),
            5 to ("14:20" to "15:05"), 6 to ("15:15" to "16:00"),
            7 to ("16:35" to "17:20"), 8 to ("17:30" to "18:15"),
            9 to ("19:20" to "20:05"), 10 to ("20:15" to "21:00")
        )

        val targetTable = if (isEvenBuilding) table2 else table1

        // 4. 核心逻辑：取第一节的开始，取最后一节的结束
        val startTime = targetTable[startSection]?.first ?: ""
        val endTime = targetTable[endSection]?.second ?: ""

        return if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
            "$startTime - $endTime"
        } else {
            section
        }
    }

    /**
     * 判断课程状态
     * @param timeRange 格式如 "08:00 - 09:40"
     */
    fun getCourseStatus(timeRange: String): CourseStatus {
        try {
            val parts = timeRange.split("-").map { it.trim() }
            if (parts.size < 2) return CourseStatus.NONE

            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val currentMinutes = now.hour * 60 + now.minute

            // 解析开始和结束时间
            val startParts = parts[0].split(":").map { it.toInt() }
            val endParts = parts[1].split(":").map { it.toInt() }

            val startMinutes = startParts[0] * 60 + startParts[1]
            val endMinutes = endParts[0] * 60 + endParts[1]

            return when {
                currentMinutes in startMinutes until endMinutes -> CourseStatus.ONGOING
                currentMinutes in (startMinutes - 30) until startMinutes -> CourseStatus.UPCOMING
                currentMinutes >= endMinutes -> CourseStatus.FINISHED
                else -> CourseStatus.NONE
            }
        } catch (_: Exception) {
            return CourseStatus.NONE
        }
    }

}