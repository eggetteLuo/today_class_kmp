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
    private data class CellEntry(
        val text: String,
    )

    actual suspend fun readAndLog(file: PlatformFile): List<CourseScheduleInstance> {
        return runCatching {
            parseWorkbook(file)
        }.onFailure { throwable ->
            AppLogger.e(TAG, "Failed to parse excel: ${file.name}", throwable)
        }.getOrElse { emptyList() }
    }

    private suspend fun parseWorkbook(file: PlatformFile): List<CourseScheduleInstance> {
        val accessGranted = file.nsUrl.startAccessingSecurityScopedResource()
        val tempDir = NSTemporaryDirectory().ifBlank { "/tmp/" }
        val tempPath = "${tempDir.trimEnd('/')}/todayclass-parse-${Clock.System.now().toEpochMilliseconds()}.xlsx".toPath()

        try {
            val fileBytes = file.readBytes()
            if (fileBytes.isEmpty()) {
                AppLogger.e(TAG, "Selected file is empty or unreadable: ${file.name}")
                return emptyList()
            }

            if (isHtmlTableFile(fileBytes)) {
                AppLogger.i(TAG, "Detected html-disguised excel: ${file.name}")
                return parseHtmlWorkbook(fileBytes, file.name)
            }

            FileSystem.SYSTEM.write(tempPath) { write(fileBytes) }
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

            val worksheetPaths = listWorksheetXmlPaths(zipFs)
            if (worksheetPaths.isEmpty()) {
                AppLogger.e(TAG, "No worksheet xml files found in xlsx: ${file.name}")
                return emptyList()
            }

            val instances = mutableListOf<CourseScheduleInstance>()
            AppLogger.i(TAG, "========== Start Excel Parse: ${file.name} ==========")

            worksheetPaths.forEachIndexed { sheetIndex, sheetPath ->
                val sheetXml = readZipTextOrNull(zipFs, candidates = listOf(sheetPath, "/$sheetPath"))
                    ?: return@forEachIndexed
                AppLogger.d(TAG, "[Sheet $sheetIndex] $sheetPath")

                val rows = parseRows(sheetXml, sharedStrings)
                val mergedRanges = parseMergedRanges(sheetXml)

                val header = findHeader(rows) ?: return@forEachIndexed
                val headerRowIndex = header.first
                val headerColIndex = header.second
                val dayColumns = (1..7).associateWith { day -> headerColIndex + day }
                val lastRowIndex = rows.keys.maxOrNull() ?: return@forEachIndexed

                for (rowIndex in (headerRowIndex + 1)..lastRowIndex) {
                    val periodText = resolveCellText(rows, mergedRanges, rowIndex, headerColIndex)
                    val startPeriod = parseStartPeriod(periodText) ?: continue

                    dayColumns.forEach { (dayOfWeek, colIndex) ->
                        val mergeInfo = getMergedCellInfo(mergedRanges, rowIndex, colIndex)
                        if (mergeInfo != null && !mergeInfo.isTopLeft) return@forEach

                        val rawCell = resolveCellText(rows, mergedRanges, rowIndex, colIndex)
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

            AppLogger.i(TAG, "Parsed instances count=${instances.size}")
            instances.forEachIndexed { index, item ->
                AppLogger.d(
                    TAG,
                    "[$index] day=${item.dayOfWeek}, period=${item.startPeriod}, week=${item.week}, " +
                        "course=${item.courseName}, teacher=${item.teacher}, room=${item.location}",
                )
            }
            AppLogger.i(TAG, "========== End Excel Parse ==========")
            return instances
        } finally {
            runCatching { FileSystem.SYSTEM.delete(tempPath) }
            if (accessGranted) {
                file.nsUrl.stopAccessingSecurityScopedResource()
            }
        }
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

    private fun isHtmlTableFile(bytes: ByteArray): Boolean {
        val head = bytes.copyOf(minOf(bytes.size, 4096)).decodeToString().lowercase()
        return "<table" in head || "<html" in head
    }

    private fun parseHtmlWorkbook(bytes: ByteArray, fileName: String): List<CourseScheduleInstance> {
        val html = bytes.decodeToString()
        val rows = parseHtmlRows(html)
        if (rows.isEmpty()) return emptyList()
        val mergedRanges = parseHtmlMergedRanges(html)

        val header = findHeaderTextRows(rows) ?: return emptyList()
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

    private fun findHeaderTextRows(rows: Map<Int, Map<Int, String>>): Pair<Int, Int>? {
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

    private fun isScheduleHeaderText(text: String): Boolean {
        val normalized = text
            .trim()
            .replace(Regex("\\s+"), "")
            .replace("／", "/")
            .replace("\\", "/")
        return normalized == "节次/周次" || (normalized.contains("节次") && normalized.contains("周次"))
    }

    private fun cellKey(rowIndex: Int, colIndex: Int): Long {
        return (rowIndex.toLong() shl 32) or (colIndex.toLong() and 0xffffffffL)
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

    private fun listWorksheetXmlPaths(zipFs: FileSystem): List<String> {
        val roots = listOf("xl/worksheets", "/xl/worksheets")
        val entries = mutableListOf<String>()

        roots.forEach { rawRoot ->
            val root = rawRoot.toPath()
            if (!zipFs.exists(root)) return@forEach
            runCatching {
                zipFs.list(root).forEach { path ->
                    val name = path.name
                    if (name.startsWith("sheet") && name.endsWith(".xml")) {
                        entries += "xl/worksheets/$name"
                    }
                }
            }
        }

        return entries.distinct().sortedBy { sheetOrder(it) }
    }

    private fun sheetOrder(path: String): Int {
        val number = Regex("sheet(\\d+)\\.xml").find(path.substringAfterLast('/'))?.groupValues?.getOrNull(1)
        return number?.toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun parseSharedStrings(xml: String): List<String> {
        val siRegex = Regex("<si[^>]*>(.*?)</si>", setOf(RegexOption.DOT_MATCHES_ALL))
        val tRegex = Regex("<t[^>]*>(.*?)</t>", setOf(RegexOption.DOT_MATCHES_ALL))
        return siRegex.findAll(xml).map { siMatch ->
            val siBody = siMatch.groupValues[1]
            tRegex.findAll(siBody).joinToString(separator = "") {
                unescapeXml(it.groupValues[1])
            }.trim()
        }.toList()
    }

    private fun parseRows(
        sheetXml: String,
        sharedStrings: List<String>,
    ): Map<Int, Map<Int, CellEntry>> {
        val rows = mutableMapOf<Int, MutableMap<Int, CellEntry>>()
        val rowRegex = Regex("<row([^>]*)>(.*?)</row>", setOf(RegexOption.DOT_MATCHES_ALL))
        val rowRefRegex = Regex("r=\"(\\d+)\"")
        val cellRegex = Regex("<c\\b([^>]*)\\s*/>|<c\\b([^>]*)>(.*?)</c>", setOf(RegexOption.DOT_MATCHES_ALL))
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

            val cols = mutableMapOf<Int, CellEntry>()
            var nextColIndex = 0

            cellRegex.findAll(rowBody).forEach { cellMatch ->
                val isSelfClosing = cellMatch.groupValues[1].isNotBlank()
                val attrs = if (isSelfClosing) cellMatch.groupValues[1] else cellMatch.groupValues[2]
                val body = if (isSelfClosing) "" else cellMatch.groupValues[3]

                val colIndex = refRegex.find(attrs)?.groupValues?.get(1)?.let(::excelColumnToIndex)
                    ?: nextColIndex
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

                cols[colIndex] = CellEntry(text = text)
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

    private data class MergedRange(
        val firstRow: Int,
        val lastRow: Int,
        val firstCol: Int,
        val lastCol: Int,
    ) {
        fun isInRange(rowIndex: Int, colIndex: Int): Boolean {
            return rowIndex in firstRow..lastRow && colIndex in firstCol..lastCol
        }
    }

    private data class MergedCellInfo(
        val firstRow: Int,
        val firstCol: Int,
        val rowSpan: Int,
        val isTopLeft: Boolean,
    )

    private fun parseMergedRanges(sheetXml: String): List<MergedRange> {
        val mergeRegex = Regex("<mergeCell[^>]*ref=\"([A-Z]+)(\\d+):([A-Z]+)(\\d+)\"[^>]*/?>")
        return mergeRegex.findAll(sheetXml).mapNotNull { match ->
            val firstCol = excelColumnToIndex(match.groupValues[1])
            val firstRow = match.groupValues[2].toIntOrNull()?.minus(1) ?: return@mapNotNull null
            val lastCol = excelColumnToIndex(match.groupValues[3])
            val lastRow = match.groupValues[4].toIntOrNull()?.minus(1) ?: return@mapNotNull null
            MergedRange(firstRow, lastRow, firstCol, lastCol)
        }.toList()
    }

    private fun getMergedCellInfo(
        mergedRanges: List<MergedRange>,
        rowIndex: Int,
        colIndex: Int,
    ): MergedCellInfo? {
        val range = mergedRanges.firstOrNull { it.isInRange(rowIndex, colIndex) } ?: return null
        return MergedCellInfo(
            firstRow = range.firstRow,
            firstCol = range.firstCol,
            rowSpan = range.lastRow - range.firstRow + 1,
            isTopLeft = rowIndex == range.firstRow && colIndex == range.firstCol,
        )
    }

    private fun resolveCellText(
        rows: Map<Int, Map<Int, CellEntry>>,
        mergedRanges: List<MergedRange>,
        rowIndex: Int,
        colIndex: Int,
    ): String {
        val raw = rows[rowIndex]?.get(colIndex)?.text.orEmpty().trim()
        if (raw.isNotBlank()) return raw

        val mergeInfo = getMergedCellInfo(mergedRanges, rowIndex, colIndex) ?: return ""
        return rows[mergeInfo.firstRow]?.get(mergeInfo.firstCol)?.text.orEmpty().trim()
    }

    private fun findHeader(rows: Map<Int, Map<Int, CellEntry>>): Pair<Int, Int>? {
        rows.keys.sorted().forEach { rowIndex ->
            val cols = rows[rowIndex] ?: return@forEach
            cols.keys.sorted().forEach { colIndex ->
                if (isScheduleHeaderText(cols[colIndex]?.text.orEmpty())) {
                    return rowIndex to colIndex
                }
            }
        }
        return null
    }

    private fun parseStartPeriod(periodText: String): Int? {
        val normalized = periodText.trim().replace(Regex("\\s+"), "")
        Regex("第([一二三四五六七八九十]+)节").find(normalized)?.let { matched ->
            return PERIOD_MAP[matched.groupValues[1]]
        }
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
        val weeks = mutableSetOf<Int>()
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
}
