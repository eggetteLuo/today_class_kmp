package com.eggetteluo.todayclass.util

import com.eggetteluo.todayclass.model.Course

object DataCleaner {
    // 增强正则：(?s) 开启跨行匹配，捕获：课程名、老师、(周次 地点)
    private val courseRegex = """(?s)(.+?)\s*\([A-Z0-9.]+\)\s*\((.+?)\)\s*\((.+?)\)""".toRegex()

    fun cleanRawText(rawContent: String, dayOfWeek: Int, defaultSection: String): List<Course> {
        val results = mutableListOf<Course>()

        // 尝试从文本中解析实际节次，如 "5-8节"，若无则使用 Excel 传入的默认节次
        val sectionRange = extractSectionFromText(rawContent) ?: defaultSection
        val sections = splitSections(sectionRange)

        courseRegex.findAll(rawContent).forEach { match ->
            val name = match.groupValues[1].trim().replace("\n", "")
            val teacher = match.groupValues[2].trim()
            val weekAndLocation = match.groupValues[3].trim()

            // 分离周次和地点，例如 "3-11单,12,16 上茶苑教9栋"
            val (weekStr, location) = splitWeekAndLocation(weekAndLocation)
            val parsedWeeks = parseWeeks(weekStr)

            // 将 1-4 节拆分为 [1-2, 3-4]
            sections.forEach { (start, end) ->
                results.add(
                    Course(
                        name = name,
                        teacher = teacher,
                        originalWeeks = weekStr,
                        weekList = parsedWeeks,
                        location = location,
                        dayOfWeek = dayOfWeek,
                        section = "$start-${end}节"
                    )
                )
            }
        }
        return results
    }

    private fun parseWeeks(weekStr: String): List<Int> {
        val weekSet = mutableSetOf<Int>()
        val parts = weekStr.replace("，", ",").split(",")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.contains("-")) {
                val rangeParts = trimmed.split("-")
                val start = rangeParts[0].filter { it.isDigit() }.toIntOrNull() ?: continue
                val end = rangeParts[1].filter { it.isDigit() }.toIntOrNull() ?: continue

                val step = when {
                    trimmed.contains("单") -> 2
                    trimmed.contains("双") -> 2
                    else -> 1
                }

                // 自动修正双周起始点
                val actualStart = if (trimmed.contains("双") && start % 2 != 0) start + 1 else start
                for (i in actualStart..end step step) weekSet.add(i)
            } else {
                val single = trimmed.filter { it.isDigit() }.toIntOrNull() ?: continue
                weekSet.add(single)
            }
        }
        return weekSet.sorted()
    }

    private fun splitWeekAndLocation(input: String): Pair<String, String> {
        val parts = input.split(Regex("\\s+"), 2)
        return if (parts.size == 2) {
            parts[0] to parts[1].removePrefix("上")
        } else {
            input to "未知地点"
        }
    }

    private fun extractSectionFromText(text: String): String? {
        val match = """(\d+)-(\d+)节""".toRegex().find(text)
        return match?.let { "${it.groupValues[1]}-${it.groupValues[2]}" }
    }

    private fun splitSections(section: String): List<Pair<Int, Int>> {
        val nums = section.split("-").mapNotNull { it.toIntOrNull() }
        if (nums.size < 2) return listOf(1 to 2)
        val result = mutableListOf<Pair<Int, Int>>()
        for (i in nums[0] until nums[1] step 2) {
            result.add(i to (i + 1).coerceAtMost(nums[1]))
        }
        return result
    }
}