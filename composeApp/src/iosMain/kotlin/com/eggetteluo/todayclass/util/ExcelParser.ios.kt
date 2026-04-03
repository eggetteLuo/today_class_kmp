package com.eggetteluo.todayclass.util

import com.eggetteluo.todayclass.model.Course
import io.github.aakira.napier.Napier
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

actual class ExcelParser actual constructor() {
    actual fun parse(bytes: ByteArray): List<Course> {
        if (bytes.isXls()) {
            return try {
                parseXls(bytes)
            } catch (e: Exception) {
                parseFromHtmlOrXmlTable(bytes).takeIf { it.isNotEmpty() }?.let {
                    Napier.i(tag = "ExcelParser") { "iOS xls 主解析失败，已通过 HTML/XML 兜底解析成功" }
                    return it.distinctBy { c -> "${c.name}-${c.dayOfWeek}-${c.section}-${c.originalWeeks}" }
                }
                throw e
            }
        }
        if (!bytes.isXlsx()) {
            parseFromHtmlOrXmlTable(bytes).takeIf { it.isNotEmpty() }?.let {
                Napier.i(tag = "ExcelParser") { "iOS 非标准 Excel，已通过 HTML/XML 兜底解析成功" }
                return it.distinctBy { c -> "${c.name}-${c.dayOfWeek}-${c.section}-${c.originalWeeks}" }
            }
            throw IllegalArgumentException("不支持的 Excel 文件格式，请使用 .xlsx 或网页表格导出文件")
        }

        val fileSystem = FileSystem.SYSTEM
        val tempZipPath = "${NSTemporaryDirectory()}today_class_${NSUUID().UUIDString}.xlsx".toPath()

        return try {
            fileSystem.write(tempZipPath) {
                write(bytes)
            }

            val zipFs = fileSystem.openZip(tempZipPath)
            val sharedStrings = readSharedStrings(zipFs)
            val sheetXml = readEntry(zipFs, "xl/worksheets/sheet1.xml")
            if (sheetXml.isEmpty()) {
                parseFromHtmlOrXmlTable(bytes).takeIf { it.isNotEmpty() }?.let {
                    return it.distinctBy { c -> "${c.name}-${c.dayOfWeek}-${c.section}-${c.originalWeeks}" }
                }
                return emptyList()
            }

            val mergedRanges = parseMergedRanges(sheetXml)
            val cellValues = parseCellValues(sheetXml, sharedStrings)
            val rowToSectionMap = mapOf(
                5 to "1-2",
                7 to "3-4",
                9 to "5-6",
                11 to "7-8",
                13 to "9-10"
            )

            val courses = mutableListOf<Course>()
            for ((row, defaultSection) in rowToSectionMap) {
                for (col in 2..8) {
                    val dayOfWeek = col - 1
                    val content = getMergedCellValue(
                        row = row,
                        col = col,
                        cellValues = cellValues,
                        mergedRanges = mergedRanges
                    )
                    if (content.isBlank()) continue

                    courses += DataCleaner.cleanRawText(
                        rawContent = content,
                        dayOfWeek = dayOfWeek,
                        defaultSection = defaultSection
                    )
                }
            }

            if (courses.isNotEmpty()) {
                courses.distinctBy { "${it.name}-${it.dayOfWeek}-${it.section}-${it.originalWeeks}" }
            } else {
                val fallback = parseFromHtmlOrXmlTable(bytes)
                if (fallback.isNotEmpty()) {
                    fallback.distinctBy { c -> "${c.name}-${c.dayOfWeek}-${c.section}-${c.originalWeeks}" }
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            parseFromHtmlOrXmlTable(bytes).takeIf { it.isNotEmpty() }?.let {
                Napier.i(tag = "ExcelParser") { "iOS xlsx 主解析失败，已通过 HTML/XML 兜底解析成功" }
                return it.distinctBy { c -> "${c.name}-${c.dayOfWeek}-${c.section}-${c.originalWeeks}" }
            }
            Napier.e(tag = "ExcelParser", throwable = e) { "iOS Excel 解析失败" }
            throw IllegalArgumentException("iOS 无法解析该 Excel 文件，请优先使用 .xlsx", e)
        } finally {
            runCatching { fileSystem.delete(tempZipPath, mustExist = false) }
        }
    }
}

private fun parseXls(bytes: ByteArray): List<Course> {
    try {
        val workbook = OLE2Reader(bytes).readWorkbookStream()
        val biff = Biff8WorkbookParser(workbook).parseFirstSheet()

        val rowToSectionMap = mapOf(
            4 to "1-2",
            6 to "3-4",
            8 to "5-6",
            10 to "7-8",
            12 to "9-10"
        )

        val courses = mutableListOf<Course>()
        for ((row, defaultSection) in rowToSectionMap) {
            for (col in 1..7) {
                val text = biff.getCellTextWithMerged(row, col).trim()
                if (text.isBlank()) continue
                courses += DataCleaner.cleanRawText(
                    rawContent = text,
                    dayOfWeek = col,
                    defaultSection = defaultSection
                )
            }
        }

        return courses.distinctBy { "${it.name}-${it.dayOfWeek}-${it.section}-${it.originalWeeks}" }
    } catch (e: Exception) {
        Napier.e(tag = "ExcelParser", throwable = e) { "iOS xls 解析失败" }
        throw IllegalArgumentException("iOS 无法解析该 .xls 文件，请优先使用 .xlsx", e)
    }
}

private fun parseFromHtmlOrXmlTable(bytes: ByteArray): List<Course> {
    val text = decodeBestEffort(bytes)
    if (!text.looksLikeTableMarkup()) return emptyList()

    val rows = parseRowsFromMarkup(text)
    if (rows.isEmpty()) return emptyList()
    return parseFromMarkupRows(rows)
}

private fun parseRowsFromMarkup(markup: String): List<List<String>> {
    val rawRows = mutableListOf<List<ParsedCell>>()
    val rowRegex = """(?is)<(?:tr|Row)\b[^>]*>(.*?)</(?:tr|Row)>""".toRegex()

    rowRegex.findAll(markup).forEach { rowMatch ->
        val rowBody = rowMatch.groupValues[1]
        val cellRegex = """(?is)<(?:td|th|Cell)\b([^>]*)>(.*?)</(?:td|th|Cell)>""".toRegex()
        val cells = mutableListOf<ParsedCell>()

        cellRegex.findAll(rowBody).forEach { cellMatch ->
            val attrs = cellMatch.groupValues[1]
            val body = cellMatch.groupValues[2]
            val dataValue = """(?is)<Data\b[^>]*>(.*?)</Data>""".toRegex().find(body)?.groupValues?.get(1) ?: body
            val text = stripMarkup(dataValue).trim()

            val index = """(?i)(?:ss:)?Index\s*=\s*"(\d+)"""".toRegex().find(attrs)?.groupValues?.get(1)?.toIntOrNull()
            val mergeDown = """(?i)(?:ss:)?MergeDown\s*=\s*"(\d+)"""".toRegex().find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val mergeAcross = """(?i)(?:ss:)?MergeAcross\s*=\s*"(\d+)"""".toRegex().find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val rowSpan = """(?i)rowspan\s*=\s*"(\d+)"""".toRegex().find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val colspan = """(?i)colspan\s*=\s*"(\d+)"""".toRegex().find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 1

            val horizontalSpan = maxOf(colspan, mergeAcross + 1).coerceAtLeast(1)
            val verticalSpan = maxOf(rowSpan, mergeDown + 1).coerceAtLeast(1)
            cells.add(
                ParsedCell(
                    text = text,
                    index = index,
                    colSpan = horizontalSpan,
                    rowSpan = verticalSpan
                )
            )
        }

        if (cells.isNotEmpty()) {
            rawRows.add(cells)
        }
    }

    if (rawRows.isEmpty()) return emptyList()

    val grid = mutableMapOf<Pair<Int, Int>, String>()
    rawRows.forEachIndexed { rowIndex, cells ->
        var col = 0
        cells.forEach { cell ->
            while (grid.containsKey(rowIndex to col)) {
                col++
            }
            if (cell.index != null && cell.index > 0) {
                col = maxOf(col, cell.index - 1)
                while (grid.containsKey(rowIndex to col)) {
                    col++
                }
            }

            for (r in rowIndex until rowIndex + cell.rowSpan) {
                for (c in col until col + cell.colSpan) {
                    grid[r to c] = cell.text
                }
            }
            col += cell.colSpan
        }
    }

    if (grid.isEmpty()) return emptyList()
    val maxRow = grid.keys.maxOf { it.first }
    val maxCol = grid.keys.maxOf { it.second }
    val rows = mutableListOf<List<String>>()
    for (r in 0..maxRow) {
        rows.add((0..maxCol).map { c -> grid[r to c].orEmpty() })
    }

    return rows
}

private fun parseFromMarkupRows(rows: List<List<String>>): List<Course> {
    val courses = mutableListOf<Course>()
    val fixedRowToSectionMap = mapOf(
        4 to "1-2",
        6 to "3-4",
        8 to "5-6",
        10 to "7-8",
        12 to "9-10"
    )
    val usedRows = mutableSetOf<Int>()

    rows.forEachIndexed { rowIndex, row ->
        val section = detectSectionFromHeader(row.firstOrNull().orEmpty()) ?: return@forEachIndexed
        usedRows.add(rowIndex)
        courses += parseCourseRow(row, section)
    }

    for ((rowIndex, section) in fixedRowToSectionMap) {
        if (rowIndex in usedRows) continue
        val row = rows.getOrNull(rowIndex) ?: continue
        courses += parseCourseRow(row, section)
    }

    return courses
}

private fun parseCourseRow(row: List<String>, defaultSection: String): List<Course> {
    val result = mutableListOf<Course>()
    for (col in 1..7) {
        val content = row.getOrNull(col)?.trim().orEmpty()
        if (content.isBlank()) continue
        result += DataCleaner.cleanRawText(
            rawContent = content,
            dayOfWeek = col,
            defaultSection = defaultSection
        )
    }
    return result
}

private fun detectSectionFromHeader(header: String): String? {
    val normalized = header.replace("第", "").replace("节", "").replace(" ", "")
    val match = """(\d{1,2})[-~～到](\d{1,2})""".toRegex().find(normalized) ?: return null
    val start = match.groupValues[1].toIntOrNull() ?: return null
    val end = match.groupValues[2].toIntOrNull() ?: return null
    if (start <= 0 || end <= 0 || end < start) return null
    return "$start-$end"
}

private fun decodeBestEffort(bytes: ByteArray): String {
    val utf8 = bytes.decodeToString()
    if (utf8.looksLikeTableMarkup()) return utf8
    return utf8
}

private fun String.looksLikeTableMarkup(): Boolean {
    val s = lowercase()
    return s.contains("<table") || s.contains("<tr") || s.contains("<td") || s.contains("<row") || s.contains("<cell")
}

private fun stripMarkup(input: String): String {
    return input
        .replace("(?is)<br\\s*/?>".toRegex(), "\n")
        .replace("(?is)<[^>]+>".toRegex(), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("\\s+".toRegex(), " ")
        .trim()
}

private data class CellRef(val row: Int, val col: Int) {
    fun toA1(): String = "${colToLetters(col)}$row"
}

private data class MergedRange(
    val start: CellRef,
    val end: CellRef
) {
    fun contains(target: CellRef): Boolean {
        return target.row in start.row..end.row && target.col in start.col..end.col
    }
}

private fun readSharedStrings(zipFs: FileSystem): List<String> {
    val xml = readEntry(zipFs, "xl/sharedStrings.xml")
    if (xml.isBlank()) return emptyList()

    val siRegex = "<si\\b[^>]*>(.*?)</si>".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    val tRegex = "<t(?:\\s[^>]*)?>(.*?)</t>".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    return siRegex.findAll(xml).map { si ->
        val fragments = tRegex.findAll(si.groupValues[1]).map { decodeXml(it.groupValues[1]) }.toList()
        fragments.joinToString("")
    }.toList()
}

private fun parseMergedRanges(sheetXml: String): List<MergedRange> {
    val refRegex = "<mergeCell\\b[^>]*ref=\"([A-Z]+\\d+):([A-Z]+\\d+)\"[^>]*/?>"
        .toRegex(RegexOption.IGNORE_CASE)

    return refRegex.findAll(sheetXml).mapNotNull { match ->
        val start = parseCellRef(match.groupValues[1])
        val end = parseCellRef(match.groupValues[2])
        if (start == null || end == null) null else MergedRange(start, end)
    }.toList()
}

private fun parseCellValues(sheetXml: String, sharedStrings: List<String>): Map<String, String> {
    val result = mutableMapOf<String, String>()
    val cellRegex = "<c\\b([^>/]*)(?:>(.*?)</c>|\\s*/>)"
        .toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    val refRegex = "r=\"([A-Z]+\\d+)\"".toRegex(RegexOption.IGNORE_CASE)
    val typeRegex = "t=\"([^\"]+)\"".toRegex(RegexOption.IGNORE_CASE)
    val valueRegex = "<v>(.*?)</v>".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    val inlineRegex = "<is\\b[^>]*>(.*?)</is>".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    val tRegex = "<t(?:\\s[^>]*)?>(.*?)</t>".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    cellRegex.findAll(sheetXml).forEach { match ->
        val attrs = match.groupValues[1]
        val body = match.groupValues.getOrElse(2) { "" }
        val ref = refRegex.find(attrs)?.groupValues?.get(1) ?: return@forEach
        val type = typeRegex.find(attrs)?.groupValues?.get(1)?.lowercase()

        val value = when (type) {
            "s" -> {
                val index = valueRegex.find(body)?.groupValues?.get(1)?.trim()?.toIntOrNull()
                if (index == null || index !in sharedStrings.indices) "" else sharedStrings[index]
            }
            "inlineStr".lowercase() -> {
                val inlineBody = inlineRegex.find(body)?.groupValues?.get(1) ?: ""
                tRegex.findAll(inlineBody).joinToString("") { decodeXml(it.groupValues[1]) }
            }
            "str" -> decodeXml(valueRegex.find(body)?.groupValues?.get(1)?.trim().orEmpty())
            else -> decodeXml(valueRegex.find(body)?.groupValues?.get(1)?.trim().orEmpty())
        }

        if (value.isNotBlank()) {
            result[ref.uppercase()] = value
        }
    }

    return result
}

private fun getMergedCellValue(
    row: Int,
    col: Int,
    cellValues: Map<String, String>,
    mergedRanges: List<MergedRange>
): String {
    val target = CellRef(row = row, col = col)
    val merged = mergedRanges.firstOrNull { it.contains(target) }
    val key = (merged?.start ?: target).toA1().uppercase()
    return cellValues[key].orEmpty().trim()
}

private fun parseCellRef(ref: String): CellRef? {
    val match = "^([A-Z]+)(\\d+)$".toRegex(RegexOption.IGNORE_CASE).find(ref.trim()) ?: return null
    val col = lettersToCol(match.groupValues[1].uppercase())
    val row = match.groupValues[2].toIntOrNull() ?: return null
    return CellRef(row = row, col = col)
}

private fun lettersToCol(letters: String): Int {
    var value = 0
    letters.forEach { ch ->
        value = value * 26 + (ch.code - 'A'.code + 1)
    }
    return value
}

private fun colToLetters(col: Int): String {
    var n = col
    val sb = StringBuilder()
    while (n > 0) {
        val rem = (n - 1) % 26
        sb.append(('A'.code + rem).toChar())
        n = (n - 1) / 26
    }
    return sb.reverse().toString()
}

private fun readEntry(zipFs: FileSystem, path: String): String {
    val entryPath = "/$path".toPath()
    if (zipFs.metadataOrNull(entryPath) == null) return ""
    val bufferedSource = zipFs.source(entryPath).buffer()
    return try {
        bufferedSource.readUtf8()
    } finally {
        bufferedSource.close()
    }
}

private fun decodeXml(value: String): String {
    return value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#10;", "\n")
        .replace("&#13;", "\r")
}

private data class MergedRange0(
    val rowStart: Int,
    val rowEnd: Int,
    val colStart: Int,
    val colEnd: Int
) {
    fun contains(row: Int, col: Int): Boolean =
        row in rowStart..rowEnd && col in colStart..colEnd
}

private data class BiffSheetData(
    val cells: Map<Pair<Int, Int>, String>,
    val mergedRanges: List<MergedRange0>
) {
    fun getCellTextWithMerged(row: Int, col: Int): String {
        cells[row to col]?.let { return it }
        val merged = mergedRanges.firstOrNull { it.contains(row, col) } ?: return ""
        return cells[merged.rowStart to merged.colStart].orEmpty()
    }
}

private class Biff8WorkbookParser(private val bytes: ByteArray) {
    private val sst = mutableListOf<String>()
    private var firstSheetOffset: Int? = null

    fun parseFirstSheet(): BiffSheetData {
        var pos = 0
        while (pos + 4 <= bytes.size) {
            val id = bytes.u16le(pos)
            val len = bytes.u16le(pos + 2)
            val dataStart = pos + 4
            val dataEnd = dataStart + len
            if (dataEnd > bytes.size) break

            when (id) {
                0x00FC -> { // SST
                    val (payload, nextPos) = collectWithContinue(pos)
                    sst.clear()
                    sst += parseSst(payload)
                    pos = nextPos
                    continue
                }
                0x0085 -> { // BoundSheet8
                    val sheetOffset = bytes.u32le(dataStart).toInt()
                    val sheetType = bytes.u8(dataStart + 5)
                    if (sheetType == 0 && firstSheetOffset == null) {
                        firstSheetOffset = sheetOffset
                    }
                }
            }
            pos = dataEnd
        }

        val start = firstSheetOffset ?: throw IllegalStateException("未找到 worksheet")
        return parseSheet(start)
    }

    private fun parseSheet(startOffset: Int): BiffSheetData {
        val cells = mutableMapOf<Pair<Int, Int>, String>()
        val mergedRanges = mutableListOf<MergedRange0>()
        var pos = startOffset

        while (pos + 4 <= bytes.size) {
            val id = bytes.u16le(pos)
            val len = bytes.u16le(pos + 2)
            val dataStart = pos + 4
            val dataEnd = dataStart + len
            if (dataEnd > bytes.size) break

            when (id) {
                0x000A -> break // EOF
                0x00FD -> { // LABELSST
                    val row = bytes.u16le(dataStart)
                    val col = bytes.u16le(dataStart + 2)
                    val sstIndex = bytes.u32le(dataStart + 6).toInt()
                    val text = sst.getOrNull(sstIndex).orEmpty()
                    if (text.isNotBlank()) cells[row to col] = text
                }
                0x0204 -> { // LABEL (older string cell)
                    val row = bytes.u16le(dataStart)
                    val col = bytes.u16le(dataStart + 2)
                    if (len >= 8) {
                        val textLen = bytes.u16le(dataStart + 6)
                        val textStart = dataStart + 8
                        val textEnd = (textStart + textLen).coerceAtMost(dataEnd)
                        val text = bytes.decodeLatin1(textStart, textEnd)
                        if (text.isNotBlank()) cells[row to col] = text
                    }
                }
                0x00E5 -> { // MERGEDCELLS
                    val count = bytes.u16le(dataStart)
                    var p = dataStart + 2
                    repeat(count) {
                        if (p + 8 > dataEnd) return@repeat
                        val r0 = bytes.u16le(p)
                        val r1 = bytes.u16le(p + 2)
                        val c0 = bytes.u16le(p + 4)
                        val c1 = bytes.u16le(p + 6)
                        mergedRanges += MergedRange0(r0, r1, c0, c1)
                        p += 8
                    }
                }
            }

            pos = dataEnd
        }
        return BiffSheetData(cells = cells, mergedRanges = mergedRanges)
    }

    private fun parseSst(payload: ByteArray): List<String> {
        if (payload.size < 8) return emptyList()
        val uniqueCount = payload.u32le(4).toInt()
        val result = ArrayList<String>(uniqueCount)
        var pos = 8

        repeat(uniqueCount) {
            if (pos + 3 > payload.size) return@repeat
            val cch = payload.u16le(pos)
            pos += 2
            val flags = payload.u8(pos)
            pos += 1

            val hasRich = (flags and 0x08) != 0
            val hasExt = (flags and 0x04) != 0
            val is16Bit = (flags and 0x01) != 0

            val richRuns = if (hasRich && pos + 2 <= payload.size) {
                val v = payload.u16le(pos); pos += 2; v
            } else 0
            val extSize = if (hasExt && pos + 4 <= payload.size) {
                val v = payload.u32le(pos).toInt(); pos += 4; v
            } else 0

            val text = if (is16Bit) {
                val byteLen = cch * 2
                val end = (pos + byteLen).coerceAtMost(payload.size)
                val s = payload.decodeUtf16Le(pos, end)
                pos = end
                s
            } else {
                val end = (pos + cch).coerceAtMost(payload.size)
                val s = payload.decodeLatin1(pos, end)
                pos = end
                s
            }

            val skip = richRuns * 4 + extSize
            pos = (pos + skip).coerceAtMost(payload.size)
            result += text
        }
        return result
    }

    private fun collectWithContinue(startPos: Int): Pair<ByteArray, Int> {
        var pos = startPos
        val out = mutableListOf<Byte>()

        while (pos + 4 <= bytes.size) {
            val id = bytes.u16le(pos)
            val len = bytes.u16le(pos + 2)
            val dataStart = pos + 4
            val dataEnd = dataStart + len
            if (dataEnd > bytes.size) break

            if (pos != startPos && id != 0x003C) break
            out += bytes.copyOfRange(dataStart, dataEnd).toList()
            pos = dataEnd
            if (pos + 4 > bytes.size || bytes.u16le(pos) != 0x003C) break
        }

        return out.toByteArray() to pos
    }
}

private class OLE2Reader(private val bytes: ByteArray) {
    fun readWorkbookStream(): ByteArray {
        require(bytes.size >= 512) { "无效 OLE2 文件" }
        val magic = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()
        )
        require(magic.indices.all { bytes[it] == magic[it] }) { "不是 OLE2 xls 文件" }

        val sectorShift = bytes.u16le(0x1E)
        val miniSectorShift = bytes.u16le(0x20)
        val sectorSize = 1 shl sectorShift
        val miniSectorSize = 1 shl miniSectorShift
        val firstDirSector = bytes.u32le(0x30).toInt()
        val miniCutoff = bytes.u32le(0x38).toInt()
        val firstMiniFatSector = bytes.u32le(0x3C).toInt()
        val miniFatSectorCount = bytes.u32le(0x40).toInt()
        val firstDifatSector = bytes.u32le(0x44).toInt()
        val difatSectorCount = bytes.u32le(0x48).toInt()

        val fatSectors = readDifatSectors(firstDifatSector, difatSectorCount)
        val fat = readFat(fatSectors, sectorSize)

        val dirStream = readNormalStream(firstDirSector, fat, sectorSize, null)
        val entries = parseDirectoryEntries(dirStream)
        val root = entries.firstOrNull { it.type == 5 } ?: error("OLE2 缺少 Root Entry")
        val workbookEntry = entries.firstOrNull {
            it.type == 2 && (it.name.equals("Workbook", true) || it.name.equals("Book", true))
        } ?: error("OLE2 缺少 Workbook 流")

        return if (workbookEntry.size >= miniCutoff) {
            readNormalStream(workbookEntry.startSector, fat, sectorSize, workbookEntry.size.toInt())
        } else {
            val miniFatStream = readNormalStream(firstMiniFatSector, fat, sectorSize, null)
            val miniFat = toIntArray(miniFatStream)
            val miniStream = readNormalStream(root.startSector, fat, sectorSize, root.size.toInt())
            readMiniStream(workbookEntry.startSector, workbookEntry.size.toInt(), miniSectorSize, miniFat, miniStream)
        }
    }

    private fun readDifatSectors(firstDifatSector: Int, difatSectorCount: Int): List<Int> {
        val sectors = mutableListOf<Int>()
        for (i in 0 until 109) {
            val v = bytes.u32le(0x4C + i * 4).toInt()
            if (v >= 0) sectors += v
        }
        var current = firstDifatSector
        repeat(difatSectorCount) {
            if (current < 0) return@repeat
            val sec = sector(current, 1 shl bytes.u16le(0x1E))
            val count = sec.size / 4 - 1
            for (i in 0 until count) {
                val v = sec.u32le(i * 4).toInt()
                if (v >= 0) sectors += v
            }
            current = sec.u32le(sec.size - 4).toInt()
        }
        return sectors
    }

    private fun readFat(fatSectors: List<Int>, sectorSize: Int): IntArray {
        val entries = mutableListOf<Int>()
        fatSectors.forEach { secId ->
            val sec = sector(secId, sectorSize)
            entries += toIntArray(sec).toList()
        }
        return entries.toIntArray()
    }

    private fun readNormalStream(startSector: Int, fat: IntArray, sectorSize: Int, expectedSize: Int?): ByteArray {
        val out = mutableListOf<Byte>()
        var sectorId = startSector
        var guard = 0
        while (sectorId >= 0 && sectorId != END_OF_CHAIN && guard < fat.size + 8) {
            out += sector(sectorId, sectorSize).toList()
            sectorId = fat.getOrElse(sectorId) { END_OF_CHAIN }
            guard++
        }
        val bytes = out.toByteArray()
        return expectedSize?.let { bytes.copyOf(minOf(it, bytes.size)) } ?: bytes
    }

    private fun readMiniStream(
        startMiniSector: Int,
        size: Int,
        miniSectorSize: Int,
        miniFat: IntArray,
        miniStream: ByteArray
    ): ByteArray {
        val out = mutableListOf<Byte>()
        var miniSector = startMiniSector
        var guard = 0
        while (miniSector >= 0 && miniSector != END_OF_CHAIN && guard < miniFat.size + 8) {
            val offset = miniSector * miniSectorSize
            val end = (offset + miniSectorSize).coerceAtMost(miniStream.size)
            if (offset >= 0 && offset < end) out += miniStream.copyOfRange(offset, end).toList()
            miniSector = miniFat.getOrElse(miniSector) { END_OF_CHAIN }
            guard++
        }
        return out.toByteArray().copyOf(minOf(size, out.size))
    }

    private fun parseDirectoryEntries(dirStream: ByteArray): List<DirEntry> {
        val entries = mutableListOf<DirEntry>()
        var pos = 0
        while (pos + 128 <= dirStream.size) {
            val nameSize = dirStream.u16le(pos + 64)
            val type = dirStream.u8(pos + 66)
            val start = dirStream.u32le(pos + 116).toInt()
            val size = dirStream.u32le(pos + 120)
            val name = if (nameSize >= 2) {
                val end = (pos + nameSize - 2).coerceAtMost(pos + 64)
                dirStream.decodeUtf16Le(pos, end)
            } else ""
            entries += DirEntry(name = name, type = type, startSector = start, size = size)
            pos += 128
        }
        return entries
    }

    private fun sector(sectorId: Int, sectorSize: Int): ByteArray {
        val offset = (sectorId + 1L) * sectorSize
        val start = offset.toInt()
        val end = (start + sectorSize).coerceAtMost(bytes.size)
        if (start !in 0 until end) return ByteArray(0)
        return bytes.copyOfRange(start, end)
    }

    private fun toIntArray(raw: ByteArray): IntArray {
        val count = raw.size / 4
        return IntArray(count) { idx -> raw.u32le(idx * 4).toInt() }
    }

    private data class DirEntry(
        val name: String,
        val type: Int,
        val startSector: Int,
        val size: Long
    )

    private companion object {
        private const val END_OF_CHAIN = -2
    }
}

private fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xFF
private fun ByteArray.u16le(offset: Int): Int {
    return u8(offset) or (u8(offset + 1) shl 8)
}
private fun ByteArray.u32le(offset: Int): Long {
    return (u8(offset).toLong()) or
        (u8(offset + 1).toLong() shl 8) or
        (u8(offset + 2).toLong() shl 16) or
        (u8(offset + 3).toLong() shl 24)
}
private fun ByteArray.decodeLatin1(start: Int, end: Int): String {
    if (start >= end) return ""
    val chars = CharArray(end - start) { i -> (this[start + i].toInt() and 0xFF).toChar() }
    return chars.concatToString()
}
private fun ByteArray.decodeUtf16Le(start: Int, end: Int): String {
    if (start >= end) return ""
    val safeEnd = if ((end - start) % 2 == 0) end else end - 1
    val chars = ArrayList<Char>((safeEnd - start) / 2)
    var p = start
    while (p + 1 < safeEnd) {
        chars += ((u8(p) or (u8(p + 1) shl 8))).toChar()
        p += 2
    }
    return chars.joinToString("")
}

private data class ParsedCell(
    val text: String,
    val index: Int?,
    val colSpan: Int,
    val rowSpan: Int
)

private fun ByteArray.isXls(): Boolean {
    val xlsMagic = byteArrayOf(
        0xD0.toByte(),
        0xCF.toByte(),
        0x11.toByte(),
        0xE0.toByte(),
        0xA1.toByte(),
        0xB1.toByte(),
        0x1A.toByte(),
        0xE1.toByte()
    )
    return size >= xlsMagic.size && xlsMagic.indices.all { this[it] == xlsMagic[it] }
}

private fun ByteArray.isXlsx(): Boolean {
    val zipMagic = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    return size >= zipMagic.size && zipMagic.indices.all { this[it] == zipMagic[it] }
}
