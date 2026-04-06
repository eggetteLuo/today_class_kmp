package com.eggetteluo.todayclasskmp.core.excel

import com.eggetteluo.todayclasskmp.core.excel.model.CourseScheduleInstance
import com.eggetteluo.todayclasskmp.core.log.AppLogger
import io.github.vinceglb.filekit.core.PlatformFile
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.openZip
import platform.Foundation.NSTemporaryDirectory
import kotlin.time.Clock

private const val TAG = "ExcelDebugReader"
private val PERIOD_MAP = mapOf(
    "一" to 1,
    "二" to 2,
    "三" to 3,
    "四" to 4,
    "五" to 5,
    "六" to 6,
    "七" to 7,
    "八" to 8,
    "九" to 9,
    "十" to 10,
)

actual object ExcelDebugReader {
    actual suspend fun readAndLog(file: PlatformFile): List<CourseScheduleInstance> {
        AppLogger.i(TAG, "iOS readAndLog called: file=${file.name}")
        val extension = file.name.substringAfterLast('.', "").lowercase()
        if (extension == "xls") {
            AppLogger.e(TAG, "iOS parser currently supports only xlsx. file=${file.name}")
            return emptyList()
        }
        if (extension != "xlsx") {
            AppLogger.e(TAG, "Unsupported excel format: .$extension file=${file.name}")
            return emptyList()
        }

        return try {
            parseXlsx(file)
        } catch (throwable: Throwable) {
            AppLogger.e(TAG, "Failed to parse xlsx on iOS: ${file.name}", throwable)
            emptyList()
        }
    }

    private suspend fun parseXlsx(file: PlatformFile): List<CourseScheduleInstance> {
        val accessGranted = file.nsUrl.startAccessingSecurityScopedResource()
        try {
            val fileBytes = file.readBytes()
            if (fileBytes.isEmpty()) {
                AppLogger.e(TAG, "Selected file is empty or unreadable: ${file.name}")
                return emptyList()
            }

            val tempDir = NSTemporaryDirectory().ifBlank { "/tmp/" }
            val tempPath = "${tempDir.trimEnd('/')}/todayclass-import-${Clock.System.now().toEpochMilliseconds()}.xlsx".toPath()
            FileSystem.SYSTEM.write(tempPath) { write(fileBytes) }

            val instances = mutableListOf<CourseScheduleInstance>()
            val zipFs = FileSystem.SYSTEM.openZip(tempPath)
            val sharedStringsXml = readZipTextOrNull(
                zipFs = zipFs,
                candidates = listOf("xl/sharedStrings.xml", "/xl/sharedStrings.xml"),
            )
            val sharedStrings = if (sharedStringsXml.isNullOrBlank()) {
                emptyList()
            } else {
                parseSharedStrings(sharedStringsXml)
            }

            val sheetXml = readZipTextOrNull(
                zipFs = zipFs,
                candidates = listOf(
                    "xl/worksheets/sheet1.xml",
                    "/xl/worksheets/sheet1.xml",
                ),
            ) ?: run {
                AppLogger.e(TAG, "Cannot find sheet1.xml in xlsx: ${file.name}")
                runCatching {
                    val roots = zipFs.list("/".toPath()).joinToString { it.toString() }
                    AppLogger.d(TAG, "Zip root entries: $roots")
                }
                return emptyList()
            }

            val rows = parseRows(sheetXml, sharedStrings)
            val mergeRowSpans = parseMergeRowSpans(sheetXml)
            val mergedRanges = parseMergedRanges(sheetXml)
            val header = rows.entries.firstOrNull { (_, cols) ->
                cols.values.any { it.trim() == "节次/周次" }
            } ?: run {
                AppLogger.e(TAG, "Header '节次/周次' not found in xlsx: ${file.name}")
                return emptyList()
            }

            val headerRowIndex = header.key
            val headerColIndex = header.value.entries.firstOrNull { it.value.trim() == "节次/周次" }?.key ?: 0
            val dayColumns = (1..7).associateWith { day -> headerColIndex + day }

            rows.entries
                .filter { it.key > headerRowIndex }
                .sortedBy { it.key }
                .forEach { (rowIndex, cols) ->
                    val periodText = cols[headerColIndex].orEmpty()
                    val startPeriod = parseStartPeriod(periodText) ?: return@forEach

                    dayColumns.forEach { (dayOfWeek, dayCol) ->
                        val rawCell = cols[dayCol].orEmpty()
                        if (rawCell.isBlank()) return@forEach
                        val rowSpan = mergeRowSpans[rowColKey(rowIndex, dayCol)] ?: 1
                        val slotCount = (rowSpan.coerceAtLeast(1) / 2).coerceAtLeast(1)
                        if (slotCount >= 2) {
                            AppLogger.d(
                                TAG,
                                "Expanded merged cell day=$dayOfWeek row=$rowIndex spanRows=$rowSpan slotCount=$slotCount",
                            )
                        }
                        instances += parseCellToInstances(rawCell, dayOfWeek, startPeriod, slotCount)
                    }
                }

            // iOS XML 某些模板下，课时行锚点不稳定，补一轮固定双节行兜底（避免漏掉周四 9-10 等课程）
            val fixedSlotRows = listOf(
                1 to (headerRowIndex + 1),
                3 to (headerRowIndex + 3),
                5 to (headerRowIndex + 5),
                7 to (headerRowIndex + 7),
                9 to (headerRowIndex + 9),
            )
            val existedKeys = instances.mapTo(mutableSetOf()) { it.instanceKey() }
            fixedSlotRows.forEach { (startPeriod, rowIndex) ->
                dayColumns.forEach { (dayOfWeek, dayCol) ->
                    val rawCell = getMergedCellText(
                        rowIndex = rowIndex,
                        colIndex = dayCol,
                        rows = rows,
                        mergedRanges = mergedRanges,
                    )
                    if (rawCell.isBlank()) return@forEach
                    val parsed = parseCellToInstances(
                        rawCell = rawCell,
                        dayOfWeek = dayOfWeek,
                        startPeriod = startPeriod,
                        slotCount = 1,
                    )
                    parsed.forEach { item ->
                        if (existedKeys.add(item.instanceKey())) {
                            AppLogger.d(
                                TAG,
                                "Fallback row-map added: day=${item.dayOfWeek}, period=${item.startPeriod}, week=${item.week}, course=${item.courseName}",
                            )
                            instances += item
                        }
                    }
                }
            }

            AppLogger.i(TAG, "iOS parsed instances count=${instances.size}")
            val periodSummary = instances
                .groupingBy { it.startPeriod }
                .eachCount()
                .entries
                .sortedBy { it.key }
                .joinToString(prefix = "{", postfix = "}") { "${it.key}=${it.value}" }
            AppLogger.d(TAG, "iOS period distribution=$periodSummary")
            instances.forEachIndexed { index, item ->
                AppLogger.d(
                    TAG,
                    "[$index] day=${item.dayOfWeek}, period=${item.startPeriod}, week=${item.week}, " +
                        "course=${item.courseName}, teacher=${item.teacher}, room=${item.location}"
                )
            }
            runCatching { FileSystem.SYSTEM.delete(tempPath) }
            return instances
        } finally {
            if (accessGranted) {
                file.nsUrl.stopAccessingSecurityScopedResource()
            }
        }
    }

    private fun readZipTextOrNull(zipFs: FileSystem, candidates: List<String>): String? {
        candidates.forEach { raw ->
            val path = raw.toPath()
            if (zipFs.exists(path)) {
                return zipFs.read(path) { readUtf8() }
            }
        }
        return null
    }

    private fun parseSharedStrings(xml: String): List<String> {
        val siRegex = Regex("<si[^>]*>(.*?)</si>", setOf(RegexOption.DOT_MATCHES_ALL))
        val tRegex = Regex("<t[^>]*>(.*?)</t>", setOf(RegexOption.DOT_MATCHES_ALL))
        return siRegex.findAll(xml).map { siMatch ->
            val siBody = siMatch.groupValues[1]
            tRegex.findAll(siBody)
                .map { unescapeXml(it.groupValues[1]) }
                .joinToString(separator = "")
                .trim()
        }.toList()
    }

    private fun parseRows(
        sheetXml: String,
        sharedStrings: List<String>,
    ): Map<Int, Map<Int, String>> {
        val rows = mutableMapOf<Int, MutableMap<Int, String>>()
        val rowRegex = Regex("<row([^>]*)>(.*?)</row>", setOf(RegexOption.DOT_MATCHES_ALL))
        val rowRefRegex = Regex("r=\"(\\d+)\"")
        val cellRegex = Regex("<c([^>]*)>(.*?)</c>|<c([^>]*)\\s*/>", setOf(RegexOption.DOT_MATCHES_ALL))
        val refRegex = Regex("r=\"([A-Z]+)(\\d+)\"")
        val typeRegex = Regex("t=\"([^\"]+)\"")
        val vRegex = Regex("<v[^>]*>(.*?)</v>", setOf(RegexOption.DOT_MATCHES_ALL))
        val inlineRegex = Regex("<is[^>]*>(.*?)</is>", setOf(RegexOption.DOT_MATCHES_ALL))
        val tRegex = Regex("<t[^>]*>(.*?)</t>", setOf(RegexOption.DOT_MATCHES_ALL))

        var nextRowIndex = 0
        rowRegex.findAll(sheetXml).forEach { rowMatch ->
            val rowAttrs = rowMatch.groupValues[1]
            val rowBody = rowMatch.groupValues[2]
            val rowIndex = rowRefRegex.find(rowAttrs)?.groupValues?.get(1)?.toIntOrNull()?.minus(1)
                ?: nextRowIndex
            nextRowIndex = rowIndex + 1
            val cols = mutableMapOf<Int, String>()
            var nextColIndex = 0

            cellRegex.findAll(rowBody).forEach { cellMatch ->
                val attrs = if (cellMatch.groupValues[1].isNotBlank()) cellMatch.groupValues[1] else cellMatch.groupValues[3]
                val body = cellMatch.groupValues[2]
                val colIndex = refRegex.find(attrs)?.groupValues?.get(1)?.let(::excelColumnToIndex) ?: nextColIndex
                nextColIndex = colIndex + 1
                val cellType = typeRegex.find(attrs)?.groupValues?.get(1).orEmpty()

                val text = when (cellType) {
                    "s" -> {
                        val idx = vRegex.find(body)?.groupValues?.get(1)?.trim()?.toIntOrNull()
                        idx?.let { sharedStrings.getOrNull(it) }.orEmpty()
                    }
                    "inlineStr" -> {
                        val inlineBody = inlineRegex.find(body)?.groupValues?.get(1).orEmpty()
                        tRegex.findAll(inlineBody)
                            .map { unescapeXml(it.groupValues[1]) }
                            .joinToString("")
                    }
                    else -> {
                        val raw = vRegex.find(body)?.groupValues?.get(1).orEmpty()
                        unescapeXml(raw)
                    }
                }.trim()

                cols[colIndex] = text
            }
            rows[rowIndex] = cols
        }
        return rows
    }

    private fun excelColumnToIndex(columnRef: String): Int {
        var result = 0
        columnRef.forEach { ch ->
            result = result * 26 + (ch.code - 'A'.code + 1)
        }
        return result - 1
    }

private fun parseStartPeriod(periodText: String): Int? {
        if (periodText.isBlank()) return null
        val normalized = periodText
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), "")
            .trim()
        val token = Regex("第([一二三四五六七八九十0-9]+)节").find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null

        token.toIntOrNull()?.let { return it }
        return chinesePeriodToInt(token)
    }

    private fun chinesePeriodToInt(token: String): Int? {
        if (token.isBlank()) return null
        PERIOD_MAP[token]?.let { return it }
        // 支持“十一/十二”这类格式，避免后续课表模板变动时再出问题
        return when {
            token == "十一" -> 11
            token == "十二" -> 12
            token.startsWith("十") -> 10 + (PERIOD_MAP[token.removePrefix("十")] ?: return 10)
            token.endsWith("十") -> (PERIOD_MAP[token.removeSuffix("十")] ?: return null) * 10
            else -> null
        }
    }

    private fun parseCellToInstances(
        rawCell: String,
        dayOfWeek: Int,
        startPeriod: Int,
        slotCount: Int,
    ): List<CourseScheduleInstance> {
        val lines = rawCell
            .replace("&#10;", "\n")
            .split('\n')
            .map { normalizeLine(it) }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) return emptyList()

        val pairs = mutableListOf<Pair<String, String>>()
        var cursor = 0
        while (cursor < lines.size) {
            val courseLine = lines[cursor]
            val scheduleLine = if (cursor + 1 < lines.size && isScheduleLine(lines[cursor + 1])) {
                lines[cursor + 1]
            } else {
                ""
            }
            pairs += courseLine to scheduleLine
            cursor += if (scheduleLine.isNotEmpty()) 2 else 1
        }

        val results = mutableListOf<CourseScheduleInstance>()
        var runStart = 0
        while (runStart < pairs.size) {
            val pair = pairs[runStart]
            var runEnd = runStart + 1
            while (runEnd < pairs.size && pairs[runEnd] == pair) {
                runEnd++
            }
            val runCount = runEnd - runStart
            val blockCount = maxOf(runCount, slotCount)
            repeat(blockCount) { indexInRun ->
                val blockStartPeriod = startPeriod + indexInRun * 2
                val (courseName, courseCode, teacher) = parseCourseLine(pair.first)
                val (weekExpr, location) = parseScheduleLine(pair.second)
                val weeks = parseWeekExpression(weekExpr)
                if (weeks.isEmpty()) {
                    AppLogger.d(
                        TAG,
                        "Skip course (no weeks parsed): day=$dayOfWeek period=$blockStartPeriod course=$courseName schedule='${pair.second}'",
                    )
                }
                weeks.forEach { week ->
                    results += CourseScheduleInstance(
                        courseName = courseName,
                        courseCode = courseCode,
                        teacher = teacher,
                        dayOfWeek = dayOfWeek,
                        startPeriod = blockStartPeriod,
                        periodCount = 2,
                        week = week,
                        location = location,
                    )
                }
            }
            runStart = runEnd
        }
        return results
    }

    private fun parseMergeRowSpans(sheetXml: String): Map<Long, Int> {
        val result = mutableMapOf<Long, Int>()
        val mergeRegex = Regex("<mergeCell[^>]*ref=\"([A-Z]+)(\\d+):([A-Z]+)(\\d+)\"[^>]*/?>")
        mergeRegex.findAll(sheetXml).forEach { match ->
            val startCol = match.groupValues[1]
            val startRow = match.groupValues[2].toIntOrNull()?.minus(1) ?: return@forEach
            val endCol = match.groupValues[3]
            val endRow = match.groupValues[4].toIntOrNull()?.minus(1) ?: return@forEach
            if (startCol != endCol) return@forEach
            val span = (endRow - startRow + 1).coerceAtLeast(1)
            result[rowColKey(startRow, excelColumnToIndex(startCol))] = span
        }
        return result
    }

    private fun rowColKey(rowIndex: Int, colIndex: Int): Long {
        return (rowIndex.toLong() shl 32) or (colIndex.toLong() and 0xffffffffL)
    }

    private data class MergedRange(
        val startRow: Int,
        val endRow: Int,
        val startCol: Int,
        val endCol: Int,
    ) {
        fun contains(rowIndex: Int, colIndex: Int): Boolean {
            return rowIndex in startRow..endRow && colIndex in startCol..endCol
        }
    }

    private fun parseMergedRanges(sheetXml: String): List<MergedRange> {
        val mergeRegex = Regex("<mergeCell[^>]*ref=\"([A-Z]+)(\\d+):([A-Z]+)(\\d+)\"[^>]*/?>")
        return mergeRegex.findAll(sheetXml).mapNotNull { match ->
            val startCol = excelColumnToIndex(match.groupValues[1])
            val startRow = match.groupValues[2].toIntOrNull()?.minus(1) ?: return@mapNotNull null
            val endCol = excelColumnToIndex(match.groupValues[3])
            val endRow = match.groupValues[4].toIntOrNull()?.minus(1) ?: return@mapNotNull null
            MergedRange(
                startRow = startRow,
                endRow = endRow,
                startCol = startCol,
                endCol = endCol,
            )
        }.toList()
    }

    private fun getMergedCellText(
        rowIndex: Int,
        colIndex: Int,
        rows: Map<Int, Map<Int, String>>,
        mergedRanges: List<MergedRange>,
    ): String {
        val direct = rows[rowIndex]?.get(colIndex).orEmpty().trim()
        if (direct.isNotBlank()) return direct
        val merged = mergedRanges.firstOrNull { it.contains(rowIndex, colIndex) } ?: return ""
        return rows[merged.startRow]?.get(merged.startCol).orEmpty().trim()
    }

    private fun parseCourseLine(line: String): Triple<String, String, String> {
        val match = Regex("^(.*?)\\s*\\((.*?)\\)\\s*\\((.*?)\\)$").find(line)
        if (match != null) {
            return Triple(
                match.groupValues[1].trim(),
                match.groupValues[2].trim(),
                match.groupValues[3].trim(),
            )
        }
        return Triple(line.trim(), "", "")
    }

    private fun parseScheduleLine(line: String): Pair<String, String> {
        if (line.isBlank()) return "" to ""
        val normalized = line
            .replace('\u00A0', ' ')
            .replace("\u200B", "")
            .replace("\uFEFF", "")
            .trim()
            .removePrefix("(")
            .removePrefix("（")
            .removeSuffix(")")
            .removeSuffix("）")
            .trim()

        // 优先从整行中抽取周次表达式（兼容未按标准括号包裹的情况）
        val weekExprPattern = Regex("(\\d+(?:-\\d+)?(?:单|双)?(?:\\s*[，,、]\\s*\\d+(?:-\\d+)?(?:单|双)?)*)")
        val weekMatch = weekExprPattern.find(normalized)
        if (weekMatch != null) {
            val weekExpr = weekMatch.value.replace(Regex("\\s+"), "")
            val location = normalized.removeRange(weekMatch.range).trim()
            return weekExpr to location
        }

        val splitIndex = normalized.indexOfFirst { it.isWhitespace() }
        if (splitIndex <= 0) return normalized to ""
        val weekExpr = normalized.substring(0, splitIndex).trim()
        val location = normalized.substring(splitIndex + 1).trim()
        return weekExpr to location
    }

    private fun parseWeekExpression(weekExpr: String): List<Int> {
        if (weekExpr.isBlank()) return emptyList()
        val weeks = mutableSetOf<Int>()
        val tokens = weekExpr
            .replace('\u00A0', ' ')
            .split(Regex("[,，、]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        tokens.forEach { token ->
            val rangeMatch = Regex("^(\\d+)-(\\d+)(单|双)?$").find(token)
            if (rangeMatch != null) {
                val start = rangeMatch.groupValues[1].toInt()
                val end = rangeMatch.groupValues[2].toInt()
                val parity = rangeMatch.groupValues[3]
                (start..end).forEach { week ->
                    if (matchesParity(week, parity)) weeks += week
                }
                return@forEach
            }
            val singleMatch = Regex("^(\\d+)(单|双)?$").find(token)
            if (singleMatch != null) {
                val week = singleMatch.groupValues[1].toInt()
                val parity = singleMatch.groupValues[2]
                if (matchesParity(week, parity)) weeks += week
            }
        }
        return weeks.toList().sorted()
    }

    private fun matchesParity(week: Int, parity: String): Boolean {
        return when (parity) {
            "单" -> week % 2 == 1
            "双" -> week % 2 == 0
            else -> true
        }
    }

    private fun unescapeXml(value: String): String {
        return value
            .replace("&#10;", "\n")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }

    private fun normalizeLine(line: String): String {
        return line
            .replace('\u00A0', ' ')
            .replace("\u200B", "")
            .replace("\uFEFF", "")
            .trim()
    }

    private fun isScheduleLine(line: String): Boolean {
        val normalized = normalizeLine(line)
        if ((normalized.startsWith("(") && normalized.endsWith(")")) ||
            (normalized.startsWith("（") && normalized.endsWith("）"))
        ) {
            return true
        }
        // 兼容非括号格式：只要包含周次表达式就视为课时安排行
        return Regex("\\d+(?:-\\d+)?(?:单|双)?").containsMatchIn(normalized)
    }

    private fun CourseScheduleInstance.instanceKey(): String {
        return "$courseName|$courseCode|$teacher|$dayOfWeek|$startPeriod|$periodCount|$week|$location"
    }
}
