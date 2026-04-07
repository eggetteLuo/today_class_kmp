package com.eggetteluo.todayclasskmp.core.excel

import com.eggetteluo.todayclasskmp.core.excel.model.CourseScheduleInstance
import com.eggetteluo.todayclasskmp.core.log.AppLogger
import io.github.vinceglb.filekit.core.PlatformFile
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.util.CellRangeAddress
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

private const val TAG = "ExcelDebugReader"
// Excel 里节次使用中文数字，先映射为起始节次整数，便于后续统一计算。
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
    /**
     * Android 端调试读取入口：
     * 1) 读取并解析 Excel；
     * 2) 出错时记录日志并返回空列表，避免影响上层页面流程。
     */
    actual suspend fun readAndLog(file: PlatformFile): List<CourseScheduleInstance> {
        return runCatching {
            parseWorkbook(file)
        }.onFailure { throwable ->
            AppLogger.e(TAG, "Failed to parse excel: ${file.name}", throwable)
        }.getOrElse { emptyList() }
    }

    private suspend fun parseWorkbook(file: PlatformFile): List<CourseScheduleInstance> {
        val bytes = file.readBytes()
        if (isHtmlTableFile(bytes)) {
            AppLogger.i(TAG, "Detected html-disguised excel: ${file.name}")
            return parseHtmlWorkbook(bytes, file.name)
        }

        val instances = mutableListOf<CourseScheduleInstance>()
        ByteArrayInputStream(bytes).use { inputStream ->
            val formatter = DataFormatter()
            WorkbookFactory.create(inputStream).use { workbook ->
                val evaluator = workbook.creationHelper.createFormulaEvaluator()
                AppLogger.i(TAG, "========== Start Excel Parse: ${file.name} ==========")
                for (sheetIndex in 0 until workbook.numberOfSheets) {
                    val sheet = workbook.getSheetAt(sheetIndex)
                    AppLogger.d(TAG, "[Sheet $sheetIndex] ${sheet.sheetName}")

                    // 在当前 sheet 里找“节次/周次”单元格作为课程表锚点。
                    val header = findHeader(sheet, formatter, evaluator) ?: continue
                    val headerRowIndex = header.first
                    val headerColIndex = header.second
                    // 锚点右侧 1..7 列对应周一到周日。
                    val dayColumns = (1..7).associateWith { day -> headerColIndex + day }

                    for (rowIndex in (headerRowIndex + 1)..sheet.lastRowNum) {
                        val row = sheet.getRow(rowIndex) ?: continue
                        val periodText = getCellText(row, headerColIndex, formatter, evaluator)
                        // 节次列无法解析时，整行视为非课程行。
                        val startPeriod = parseStartPeriod(periodText) ?: continue

                        dayColumns.forEach { (dayOfWeek, colIndex) ->
                            val mergeInfo = getMergedCellInfo(sheet, rowIndex, colIndex)
                            // 合并单元格只在左上角执行一次解析，避免重复生成实例。
                            if (mergeInfo != null && !mergeInfo.isTopLeft) return@forEach
                            val rawCell = if (mergeInfo != null) {
                                getCellText(sheet.getRow(mergeInfo.firstRow) ?: row, mergeInfo.firstCol, formatter, evaluator)
                            } else {
                                getCellText(row, colIndex, formatter, evaluator)
                            }
                            if (rawCell.isBlank()) return@forEach
                            // 模板里通常 2 行代表 2 节课；合并行数用于估算连续课块数量。
                            val slotCount = (mergeInfo?.rowSpan ?: 1).coerceAtLeast(1) / 2
                            if (slotCount >= 2) {
                                AppLogger.d(
                                    TAG,
                                    "Expanded merged cell day=$dayOfWeek row=$rowIndex spanRows=${mergeInfo?.rowSpan} slotCount=$slotCount",
                                )
                            }
                            instances += parseCellToInstances(
                                rawCell = rawCell,
                                dayOfWeek = dayOfWeek,
                                startPeriod = startPeriod,
                                slotCount = slotCount.coerceAtLeast(1),
                            )
                        }
                    }
                }
            }
        }

        AppLogger.i(TAG, "Parsed instances count=${instances.size}")
        instances.forEachIndexed { index, item ->
            AppLogger.d(
                TAG,
                "[$index] day=${item.dayOfWeek}, period=${item.startPeriod}, week=${item.week}, " +
                    "course=${item.courseName}, teacher=${item.teacher}, room=${item.location}"
            )
        }
        AppLogger.i(TAG, "========== End Excel Parse ==========")
        return instances
    }

    private data class HtmlMergedRange(
        val firstRow: Int,
        val lastRow: Int,
        val firstCol: Int,
        val lastCol: Int,
    ) {
        fun isInRange(rowIndex: Int, colIndex: Int): Boolean {
            return rowIndex in firstRow..lastRow && colIndex in firstCol..lastCol
        }
    }

    private data class HtmlMergedCellInfo(
        val firstRow: Int,
        val firstCol: Int,
        val rowSpan: Int,
        val isTopLeft: Boolean,
    )

    private fun parseHtmlWorkbook(bytes: ByteArray, fileName: String): List<CourseScheduleInstance> {
        val html = decodeHtmlBytes(bytes)
        val rows = parseHtmlRows(html)
        if (rows.isEmpty()) return emptyList()
        val mergedRanges = parseHtmlMergedRanges(html)

        val header = findHeader(rows) ?: return emptyList()
        val headerRowIndex = header.first
        val headerColIndex = header.second
        val dayColumns = (1..7).associateWith { day -> headerColIndex + day }
        val lastRowIndex = rows.keys.maxOrNull() ?: return emptyList()

        val instances = mutableListOf<CourseScheduleInstance>()
        AppLogger.i(TAG, "========== Start Html Excel Parse: $fileName ==========")
        for (rowIndex in (headerRowIndex + 1)..lastRowIndex) {
            val periodText = resolveHtmlCellText(rows, mergedRanges, rowIndex, headerColIndex)
            val startPeriod = parseStartPeriod(periodText) ?: continue

            dayColumns.forEach { (dayOfWeek, colIndex) ->
                val mergeInfo = getHtmlMergedCellInfo(mergedRanges, rowIndex, colIndex)
                if (mergeInfo != null && !mergeInfo.isTopLeft) return@forEach

                val rawCell = resolveHtmlCellText(rows, mergedRanges, rowIndex, colIndex)
                if (rawCell.isBlank()) return@forEach
                val slotCount = (mergeInfo?.rowSpan ?: 1).coerceAtLeast(1) / 2
                instances += parseCellToInstances(
                    rawCell = rawCell,
                    dayOfWeek = dayOfWeek,
                    startPeriod = startPeriod,
                    slotCount = slotCount.coerceAtLeast(1),
                )
            }
        }
        AppLogger.i(TAG, "Parsed html instances count=${instances.size}")
        AppLogger.i(TAG, "========== End Html Excel Parse ==========")
        return instances
    }

    private fun isHtmlTableFile(bytes: ByteArray): Boolean {
        val head = bytes.copyOf(minOf(bytes.size, 4096)).decodeToString()
            .lowercase()
        return "<table" in head || "<html" in head
    }

    private fun decodeHtmlBytes(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        val charsetName = Regex("charset\\s*=\\s*['\\\"]?([a-zA-Z0-9_\\-]+)")
            .find(utf8)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (charsetName.isNullOrBlank()) return utf8
        return runCatching {
            bytes.toString(Charset.forName(charsetName))
        }.getOrElse { utf8 }
    }

    private fun parseHtmlRows(html: String): Map<Int, Map<Int, String>> {
        val rows = mutableMapOf<Int, MutableMap<Int, String>>()
        val occupied = mutableSetOf<Long>()
        val trRegex = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val tdRegex = Regex("<t[dh]([^>]*)>(.*?)</t[dh]>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val rowSpanRegex = Regex("rowspan\\s*=\\s*['\\\"]?(\\d+)", RegexOption.IGNORE_CASE)
        val colSpanRegex = Regex("colspan\\s*=\\s*['\\\"]?(\\d+)", RegexOption.IGNORE_CASE)

        var rowIndex = 0
        trRegex.findAll(html).forEach { tr ->
            val cols = mutableMapOf<Int, String>()
            var colIndex = 0
            tdRegex.findAll(tr.groupValues[1]).forEach { td ->
                while (occupied.contains(cellKey(rowIndex, colIndex))) colIndex++
                val attrs = td.groupValues[1]
                val body = td.groupValues[2]
                val rowSpan = rowSpanRegex.find(attrs)?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val colSpan = colSpanRegex.find(attrs)?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val text = htmlCellToText(body)
                cols[colIndex] = text
                for (r in rowIndex until (rowIndex + rowSpan)) {
                    for (c in colIndex until (colIndex + colSpan)) {
                        occupied += cellKey(r, c)
                    }
                }
                colIndex++
                while (occupied.contains(cellKey(rowIndex, colIndex))) colIndex++
            }
            rows[rowIndex] = cols
            rowIndex++
        }
        return rows
    }

    private fun parseHtmlMergedRanges(html: String): List<HtmlMergedRange> {
        val ranges = mutableListOf<HtmlMergedRange>()
        val occupied = mutableSetOf<Long>()
        val trRegex = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val tdRegex = Regex("<t[dh]([^>]*)>(.*?)</t[dh]>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val rowSpanRegex = Regex("rowspan\\s*=\\s*['\\\"]?(\\d+)", RegexOption.IGNORE_CASE)
        val colSpanRegex = Regex("colspan\\s*=\\s*['\\\"]?(\\d+)", RegexOption.IGNORE_CASE)

        var rowIndex = 0
        trRegex.findAll(html).forEach { tr ->
            var colIndex = 0
            tdRegex.findAll(tr.groupValues[1]).forEach { td ->
                while (occupied.contains(cellKey(rowIndex, colIndex))) colIndex++
                val attrs = td.groupValues[1]
                val rowSpan = rowSpanRegex.find(attrs)?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val colSpan = colSpanRegex.find(attrs)?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                if (rowSpan > 1 || colSpan > 1) {
                    ranges += HtmlMergedRange(
                        firstRow = rowIndex,
                        lastRow = rowIndex + rowSpan - 1,
                        firstCol = colIndex,
                        lastCol = colIndex + colSpan - 1,
                    )
                }
                for (r in rowIndex until (rowIndex + rowSpan)) {
                    for (c in colIndex until (colIndex + colSpan)) {
                        occupied += cellKey(r, c)
                    }
                }
                colIndex++
                while (occupied.contains(cellKey(rowIndex, colIndex))) colIndex++
            }
            rowIndex++
        }
        return ranges
    }

    private fun htmlCellToText(body: String): String {
        val unescaped = body
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
        return decodeNumericHtmlEntities(unescaped).trim()
    }

    private fun decodeNumericHtmlEntities(text: String): String {
        var result = text
        result = Regex("&#(\\d+);").replace(result) { match ->
            val code = match.groupValues[1].toIntOrNull() ?: return@replace match.value
            code.toChar().toString()
        }
        result = Regex("&#x([0-9a-fA-F]+);").replace(result) { match ->
            val code = match.groupValues[1].toIntOrNull(16) ?: return@replace match.value
            code.toChar().toString()
        }
        return result
    }

    private fun resolveHtmlCellText(
        rows: Map<Int, Map<Int, String>>,
        mergedRanges: List<HtmlMergedRange>,
        rowIndex: Int,
        colIndex: Int,
    ): String {
        val raw = rows[rowIndex]?.get(colIndex).orEmpty().trim()
        if (raw.isNotBlank()) return raw
        val merge = getHtmlMergedCellInfo(mergedRanges, rowIndex, colIndex) ?: return ""
        return rows[merge.firstRow]?.get(merge.firstCol).orEmpty().trim()
    }

    private fun getHtmlMergedCellInfo(
        mergedRanges: List<HtmlMergedRange>,
        rowIndex: Int,
        colIndex: Int,
    ): HtmlMergedCellInfo? {
        val range = mergedRanges.firstOrNull { it.isInRange(rowIndex, colIndex) } ?: return null
        return HtmlMergedCellInfo(
            firstRow = range.firstRow,
            firstCol = range.firstCol,
            rowSpan = range.lastRow - range.firstRow + 1,
            isTopLeft = rowIndex == range.firstRow && colIndex == range.firstCol,
        )
    }

    private fun findHeader(rows: Map<Int, Map<Int, String>>): Pair<Int, Int>? {
        rows.keys.sorted().forEach { row ->
            val cols = rows[row] ?: return@forEach
            cols.keys.sorted().forEach { col ->
                if (isScheduleHeaderText(cols[col].orEmpty())) {
                    return row to col
                }
            }
        }
        return null
    }

    private fun cellKey(rowIndex: Int, colIndex: Int): Long {
        return (rowIndex.toLong() shl 32) or (colIndex.toLong() and 0xffffffffL)
    }

    private fun findHeader(
        sheet: Sheet,
        formatter: DataFormatter,
        evaluator: FormulaEvaluator,
    ): Pair<Int, Int>? {
        // 全表扫描“节次/周次”标记，支持不同模板中表头位置不固定的情况。
        for (rowIndex in sheet.firstRowNum..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            val lastCellExclusive = row.lastCellNum.toInt().coerceAtLeast(0)
            for (colIndex in 0 until lastCellExclusive) {
                val text = getCellText(row, colIndex, formatter, evaluator)
                if (isScheduleHeaderText(text)) return rowIndex to colIndex
            }
        }
        return null
    }

    private fun isScheduleHeaderText(text: String): Boolean {
        val normalized = text
            .trim()
            .replace(Regex("\\s+"), "")
            .replace("／", "/")
            .replace("\\", "/")
        return normalized == "节次/周次" || (normalized.contains("节次") && normalized.contains("周次"))
    }

    private fun getCellText(
        row: Row,
        colIndex: Int,
        formatter: DataFormatter,
        evaluator: FormulaEvaluator,
    ): String {
        val cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) ?: return ""
        return formatter.formatCellValue(cell, evaluator).trim()
    }

    private fun parseStartPeriod(periodText: String): Int? {
        val normalized = periodText.trim().replace(Regex("\\s+"), "")
        // 示例：第六节 -> 6
        Regex("第([一二三四五六七八九十]+)节").find(normalized)?.let { matched ->
            return PERIOD_MAP[matched.groupValues[1]]
        }
        // 示例：第1节 / 1-2节 / 1
        Regex("第?(\\d+)(?:-(\\d+))?节?").find(normalized)?.let { matched ->
            return matched.groupValues[1].toIntOrNull()
        }
        return null
    }

    private fun parseCellToInstances(
        rawCell: String,
        dayOfWeek: Int,
        startPeriod: Int,
        slotCount: Int,
    ): List<CourseScheduleInstance> {
        // 单元格通常是“课程行 + 时间地点行”交替，先做去空白和分行。
        val lines = rawCell
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) return emptyList()

        val pairs = mutableListOf<Pair<String, String>>()
        var cursor = 0
        while (cursor < lines.size) {
            val courseLine = lines[cursor]
            val scheduleLine = if (cursor + 1 < lines.size && lines[cursor + 1].startsWith("(")) {
                lines[cursor + 1]
            } else {
                ""
            }
            // 组装成 (课程信息, 周次地点) 二元组。
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
            // 连续相同课程按出现次数展开；若单元格跨多时段，至少展开到 slotCount。
            val blockCount = maxOf(runCount, slotCount)
            repeat(blockCount) { indexInRun ->
                val blockStartPeriod = startPeriod + indexInRun * 2
                val (courseName, courseCode, teacher) = parseCourseLine(pair.first)
                val (weekExpr, location) = parseScheduleLine(pair.second)
                val weeks = parseWeekExpression(weekExpr)
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

    private data class MergedCellInfo(
        val firstRow: Int,
        val firstCol: Int,
        val rowSpan: Int,
        val isTopLeft: Boolean,
    )

    private fun getMergedCellInfo(
        sheet: Sheet,
        rowIndex: Int,
        colIndex: Int,
    ): MergedCellInfo? {
        // POI 用 merged regions 管理合并块，这里按坐标反查所在合并区域。
        for (i in 0 until sheet.numMergedRegions) {
            val region: CellRangeAddress = sheet.getMergedRegion(i)
            if (region.isInRange(rowIndex, colIndex)) {
                return MergedCellInfo(
                    firstRow = region.firstRow,
                    firstCol = region.firstColumn,
                    rowSpan = region.lastRow - region.firstRow + 1,
                    isTopLeft = rowIndex == region.firstRow && colIndex == region.firstColumn,
                )
            }
        }
        return null
    }

    private fun parseCourseLine(line: String): Triple<String, String, String> {
        // 约定格式：课程名 (课程号) (教师名)
        val match = Regex("^(.*?)\\s*\\((.*?)\\)\\s*\\((.*?)\\)$").find(line)
        if (match != null) {
            return Triple(
                match.groupValues[1].trim(),
                match.groupValues[2].trim(),
                match.groupValues[3].trim(),
            )
        }
        // 兜底：仅保留课程名，避免因格式偏差丢失整条数据。
        return Triple(line.trim(), "", "")
    }

    private fun parseScheduleLine(line: String): Pair<String, String> {
        if (line.isBlank()) return "" to ""
        // 约定格式：(周次表达式 地点)，例如：(1-16单 教A-101)
        val normalized = line.trim().removePrefix("(").removeSuffix(")").trim()
        val splitIndex = normalized.indexOfFirst { it.isWhitespace() }
        if (splitIndex <= 0) return normalized to ""
        val weekExpr = normalized.substring(0, splitIndex).trim()
        val location = normalized.substring(splitIndex + 1).trim()
        return weekExpr to location
    }

    private fun parseWeekExpression(weekExpr: String): List<Int> {
        if (weekExpr.isBlank()) return emptyList()
        val weeks = sortedSetOf<Int>()
        // 支持逗号分隔组合：1-8单,10,12-16双
        val tokens = weekExpr.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        tokens.forEach { token ->
            val rangeMatch = Regex("^(\\d+)-(\\d+)([单双])?$").find(token)
            if (rangeMatch != null) {
                val start = rangeMatch.groupValues[1].toInt()
                val end = rangeMatch.groupValues[2].toInt()
                val parity = rangeMatch.groupValues[3]
                (start..end).forEach { week ->
                    if (matchesParity(week, parity)) weeks += week
                }
                return@forEach
            }
            val singleMatch = Regex("^(\\d+)([单双])?$").find(token)
            if (singleMatch != null) {
                val week = singleMatch.groupValues[1].toInt()
                val parity = singleMatch.groupValues[2]
                if (matchesParity(week, parity)) weeks += week
            }
        }
        return weeks.toList()
    }

    private fun matchesParity(week: Int, parity: String): Boolean {
        // 空后缀表示不过滤单双周。
        return when (parity) {
            "单" -> week % 2 == 1
            "双" -> week % 2 == 0
            else -> true
        }
    }
}
