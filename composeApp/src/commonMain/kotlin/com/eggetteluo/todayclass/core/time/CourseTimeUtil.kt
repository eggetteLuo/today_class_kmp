package com.eggetteluo.todayclass.core.time

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

enum class CourseStatus {
    ONGOING,
    UPCOMING,
    NONE,
    FINISHED,
}

private val ODD_BUILDING_TABLE = mapOf(
    1 to ("08:00" to "08:45"),
    2 to ("08:55" to "09:40"),
    3 to ("10:25" to "11:10"),
    4 to ("11:20" to "12:05"),
    5 to ("14:00" to "14:45"),
    6 to ("14:55" to "15:40"),
    7 to ("16:25" to "17:10"),
    8 to ("17:20" to "18:05"),
    9 to ("19:20" to "20:05"),
    10 to ("20:15" to "21:00"),
)

private val EVEN_BUILDING_TABLE = mapOf(
    1 to ("08:20" to "09:05"),
    2 to ("09:15" to "10:00"),
    3 to ("10:35" to "11:20"),
    4 to ("11:30" to "12:15"),
    5 to ("14:20" to "15:05"),
    6 to ("15:15" to "16:00"),
    7 to ("16:35" to "17:20"),
    8 to ("17:30" to "18:15"),
    9 to ("19:20" to "20:05"),
    10 to ("20:15" to "21:00"),
)

fun getFormattedTimeRange(startPeriod: Int, periodCount: Int, location: String): String {
    val endPeriod = startPeriod + periodCount - 1
    val table = resolveTable(location)
    val startTime = table[startPeriod]?.first.orEmpty()
    val endTime = table[endPeriod]?.second.orEmpty()
    return if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
        "$startTime - $endTime"
    } else {
        "第 $startPeriod-$endPeriod 节"
    }
}

fun getCourseStatus(timeRange: String, isToday: Boolean): CourseStatus {
    if (!isToday) return CourseStatus.NONE
    val parts = timeRange.split("-").map { it.trim() }
    if (parts.size < 2) return CourseStatus.NONE

    val startPair = parts[0].split(":").mapNotNull { it.toIntOrNull() }
    val endPair = parts[1].split(":").mapNotNull { it.toIntOrNull() }
    if (startPair.size < 2 || endPair.size < 2) return CourseStatus.NONE

    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
    val nowMinutes = now.hour * 60 + now.minute
    val startMinutes = startPair[0] * 60 + startPair[1]
    val endMinutes = endPair[0] * 60 + endPair[1]

    return when {
        nowMinutes in startMinutes until endMinutes -> CourseStatus.ONGOING
        nowMinutes > endMinutes -> CourseStatus.FINISHED
        (startMinutes - nowMinutes).minutes <= 30.minutes -> CourseStatus.UPCOMING
        else -> CourseStatus.NONE
    }
}

private fun resolveTable(location: String): Map<Int, Pair<String, String>> {
    val buildingNumber = Regex("\\d+").find(location)?.value?.toIntOrNull() ?: 1
    return if (buildingNumber % 2 == 0) EVEN_BUILDING_TABLE else ODD_BUILDING_TABLE
}
