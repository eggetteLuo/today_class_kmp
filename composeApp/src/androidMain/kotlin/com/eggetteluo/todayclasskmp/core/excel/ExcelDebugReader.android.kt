package com.eggetteluo.todayclasskmp.core.excel

import com.eggetteluo.todayclasskmp.core.excel.model.CourseScheduleInstance
import com.eggetteluo.todayclasskmp.core.log.AppLogger
import io.github.vinceglb.filekit.core.PlatformFile
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.util.CellRangeAddress
import java.io.ByteArrayInputStream

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
        return runCatching {
            parseWorkbook(file)
        }.onFailure { throwable ->
            AppLogger.e(TAG, "Failed to parse excel: ${file.name}", throwable)
        }.getOrElse { emptyList() }
    }

    private suspend fun parseWorkbook(file: PlatformFile): List<CourseScheduleInstance> {
        val instances = mutableListOf<CourseScheduleInstance>()
        val bytes = file.readBytes()
        ByteArrayInputStream(bytes).use { inputStream ->
            val formatter = DataFormatter()
            WorkbookFactory.create(inputStream).use { workbook ->
                val evaluator = workbook.creationHelper.createFormulaEvaluator()
                AppLogger.i(TAG, "========== Start Excel Parse: ${file.name} ==========")
                for (sheetIndex in 0 until workbook.numberOfSheets) {
                    val sheet = workbook.getSheetAt(sheetIndex)
                    AppLogger.d(TAG, "[Sheet $sheetIndex] ${sheet.sheetName}")

                    val header = findHeader(sheet, formatter, evaluator) ?: continue
                    val headerRowIndex = header.first
                    val headerColIndex = header.second
                    val dayColumns = (1..7).associateWith { day -> headerColIndex + day }

                    for (rowIndex in (headerRowIndex + 1)..sheet.lastRowNum) {
                        val row = sheet.getRow(rowIndex) ?: continue
                        val periodText = getCellText(row, headerColIndex, formatter, evaluator)
                        val startPeriod = parseStartPeriod(periodText) ?: continue

                        dayColumns.forEach { (dayOfWeek, colIndex) ->
                            val mergeInfo = getMergedCellInfo(sheet, rowIndex, colIndex)
                            if (mergeInfo != null && !mergeInfo.isTopLeft) return@forEach
                            val rawCell = if (mergeInfo != null) {
                                getCellText(sheet.getRow(mergeInfo.firstRow) ?: row, mergeInfo.firstCol, formatter, evaluator)
                            } else {
                                getCellText(row, colIndex, formatter, evaluator)
                            }
                            if (rawCell.isBlank()) return@forEach
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

    private fun findHeader(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        formatter: DataFormatter,
        evaluator: org.apache.poi.ss.usermodel.FormulaEvaluator,
    ): Pair<Int, Int>? {
        for (rowIndex in sheet.firstRowNum..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            val lastCellExclusive = row.lastCellNum.toInt().coerceAtLeast(0)
            for (colIndex in 0 until lastCellExclusive) {
                val text = getCellText(row, colIndex, formatter, evaluator)
                if (text == "节次/周次") return rowIndex to colIndex
            }
        }
        return null
    }

    private fun getCellText(
        row: Row,
        colIndex: Int,
        formatter: DataFormatter,
        evaluator: org.apache.poi.ss.usermodel.FormulaEvaluator,
    ): String {
        val cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) ?: return ""
        return formatter.formatCellValue(cell, evaluator).trim()
    }

    private fun parseStartPeriod(periodText: String): Int? {
        val matched = Regex("第([一二三四五六七八九十]+)节").find(periodText) ?: return null
        val zh = matched.groupValues[1]
        return PERIOD_MAP[zh]
    }

    private fun parseCellToInstances(
        rawCell: String,
        dayOfWeek: Int,
        startPeriod: Int,
        slotCount: Int,
    ): List<CourseScheduleInstance> {
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
        sheet: org.apache.poi.ss.usermodel.Sheet,
        rowIndex: Int,
        colIndex: Int,
    ): MergedCellInfo? {
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
        return when (parity) {
            "单" -> week % 2 == 1
            "双" -> week % 2 == 0
            else -> true
        }
    }
}
