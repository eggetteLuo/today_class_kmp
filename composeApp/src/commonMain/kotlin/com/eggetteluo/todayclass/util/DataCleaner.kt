package com.eggetteluo.todayclass.util

import com.eggetteluo.todayclass.model.Course

object DataCleaner {
    // 正则表达式：匹配 课程名 (代码) (老师) \n (周次 地点)
    private val courseRegex = """(.+?)\s\(.*?\)\s\((.+?)\)\s*\(([\d\-,]+)\s+(.+?)\)""".toRegex()

    /**
     * 清理表格数据为课表数据
     */
    fun cleanRawText(rawContent: String, dayOfWeek: Int, section: String): List<Course> {
        return courseRegex.findAll(rawContent).map { match ->
            val weekStr = match.groupValues[3].trim()
            Course(
                name = match.groupValues[1].trim(),
                teacher = match.groupValues[2].trim(),
                originalWeeks = weekStr,
                weekList = parseWeeks(weekStr),
                location = match.groupValues[4].trim(),
                dayOfWeek = dayOfWeek,
                section = section
            )
        }.toList()
    }

    /**
     * 解析周次数据
     */
    private fun parseWeeks(weekStr: String): List<Int> {
        val weekSet = mutableSetOf<Int>()
        val parts = weekStr.split(",", "，")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val range = trimmed.split("-")
                if (range.size == 2) {
                    val start = range[0].filter { it.isDigit() }.toIntOrNull() ?: continue
                    val end = range[1].filter { it.isDigit() }.toIntOrNull() ?: continue
                    for (i in start..end) weekSet.add(i)
                }
            } else {
                val single = trimmed.filter { it.isDigit() }.toIntOrNull() ?: continue
                weekSet.add(single)
            }
        }
        return weekSet.sorted()
    }
}