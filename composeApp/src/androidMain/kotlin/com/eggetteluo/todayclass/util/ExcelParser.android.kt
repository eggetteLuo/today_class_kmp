package com.eggetteluo.todayclass.util

import com.eggetteluo.todayclass.model.Course
import io.github.aakira.napier.Napier
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

actual class ExcelParser actual constructor() {
    actual fun parse(bytes: ByteArray): List<Course> {
        try {
            openWorkbook(bytes).use { workbook ->
                val parsed = parseFromWorkbook(workbook)
                if (parsed.isNotEmpty()) {
                    return parsed.distinctBy { "${it.name}-${it.dayOfWeek}-${it.section}-${it.originalWeeks}" }
                }
            }
        } catch (primaryError: Exception) {
            val fallback = parseFromHtmlOrXmlTable(bytes)
            if (fallback.isNotEmpty()) {
                Napier.i(tag = "ExcelParser") { "POI 解析失败，已通过 HTML/XML 兜底解析成功" }
                return fallback.distinctBy { "${it.name}-${it.dayOfWeek}-${it.section}-${it.originalWeeks}" }
            }

            Napier.e(tag = "ExcelParser", throwable = primaryError) { "Excel 解析失败" }
            throw IllegalArgumentException("无法解析该 Excel 文件，请确认它是真正的 Excel 文件（不是网页另存）", primaryError)
        }

        // openWorkbook 成功但未解析出有效课程时，也尝试文本表格兜底
        val fallback = parseFromHtmlOrXmlTable(bytes)
        if (fallback.isNotEmpty()) {
            return fallback.distinctBy { "${it.name}-${it.dayOfWeek}-${it.section}-${it.originalWeeks}" }
        }

        throw IllegalArgumentException("课表内容为空或格式不匹配，未识别到可导入的课程")
    }

    private fun openWorkbook(bytes: ByteArray): Workbook {
        var lastError: Throwable? = null

        fun tryOpenXls(): Workbook? {
            return try {
                HSSFWorkbook(ByteArrayInputStream(bytes))
            } catch (t: Throwable) {
                lastError = t
                null
            }
        }

        fun tryOpenXlsx(): Workbook? {
            return try {
                XSSFWorkbook(ByteArrayInputStream(bytes))
            } catch (t: Throwable) {
                lastError = t
                null
            }
        }

        // 优先按文件头猜测格式；失败后再双向兜底，兼容“后缀与内容不一致”的文件。
        val workbook = when {
            bytes.isXls() -> tryOpenXls() ?: tryOpenXlsx()
            bytes.isXlsx() -> tryOpenXlsx() ?: tryOpenXls()
            else -> tryOpenXls() ?: tryOpenXlsx()
        }

        return workbook ?: throw IllegalArgumentException(
            "不支持的 Excel 文件或文件已损坏",
            lastError
        )
    }

    private fun parseFromWorkbook(workbook: Workbook): List<Course> {
        val sheet = workbook.getSheetAt(0) ?: return emptyList()
        return parseFromCellGrid { rowIndex, colIndex ->
            getMergedCellValue(sheet, rowIndex, colIndex)
        }
    }

    private fun parseFromHtmlOrXmlTable(bytes: ByteArray): List<Course> {
        val text = decodeBestEffort(bytes)
        if (!text.looksLikeTableMarkup()) return emptyList()

        val rows = parseRowsFromMarkup(text)
        if (rows.isEmpty()) return emptyList()

        return parseFromCellGrid { rowIndex, colIndex ->
            rows.getOrNull(rowIndex)?.getOrNull(colIndex).orEmpty()
        }
    }

    private fun parseFromCellGrid(getCellText: (rowIndex: Int, colIndex: Int) -> String): List<Course> {
        val rowToSectionMap = mapOf(
            4 to "1-2",
            6 to "3-4",
            8 to "5-6",
            10 to "7-8",
            12 to "9-10"
        )

        val courseList = mutableListOf<Course>()
        for ((rowIndex, defaultSection) in rowToSectionMap) {
            for (colIndex in 1..7) {
                val rawContent = getCellText(rowIndex, colIndex).trim()
                if (rawContent.isBlank()) continue

                val parsed = DataCleaner.cleanRawText(
                    rawContent = rawContent,
                    dayOfWeek = colIndex,
                    defaultSection = defaultSection
                )
                courseList.addAll(parsed)
            }
        }
        return courseList
    }

    private fun parseRowsFromMarkup(markup: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val rowRegex = """(?is)<(?:tr|Row)\b[^>]*>(.*?)</(?:tr|Row)>""".toRegex()

        rowRegex.findAll(markup).forEach { rowMatch ->
            val rowBody = rowMatch.groupValues[1]
            val cellRegex = """(?is)<(?:td|th|Cell)\b([^>]*)>(.*?)</(?:td|th|Cell)>""".toRegex()
            val cells = mutableListOf<String>()

            cellRegex.findAll(rowBody).forEach { cellMatch ->
                val attrs = cellMatch.groupValues[1]
                val body = cellMatch.groupValues[2]

                val dataValue = """(?is)<Data\b[^>]*>(.*?)</Data>""".toRegex().find(body)?.groupValues?.get(1) ?: body
                val text = stripMarkup(dataValue).trim()

                val index = """(?i)(?:ss:)?Index\s*=\s*"(\d+)"""".toRegex().find(attrs)?.groupValues?.get(1)?.toIntOrNull()
                val mergeAcross = """(?i)(?:ss:)?MergeAcross\s*=\s*"(\d+)"""".toRegex().find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val colspan = """(?i)colspan\s*=\s*"(\d+)"""".toRegex().find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 1

                if (index != null && index > cells.size + 1) {
                    repeat(index - (cells.size + 1)) { cells.add("") }
                }

                val span = maxOf(colspan, mergeAcross + 1)
                repeat(span.coerceAtLeast(1)) { cells.add(text) }
            }

            if (cells.isNotEmpty()) {
                rows.add(cells)
            }
        }

        return rows
    }

    private fun decodeBestEffort(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        if (utf8.looksLikeTableMarkup()) return utf8

        val gb18030 = bytes.toString(Charset.forName("GB18030"))
        if (gb18030.looksLikeTableMarkup()) return gb18030

        val utf16le = bytes.toString(Charsets.UTF_16LE)
        if (utf16le.looksLikeTableMarkup()) return utf16le

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
            .replace("&#39;", "'")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun getMergedCellValue(sheet: Sheet, row: Int, col: Int): String {
        val formatter = DataFormatter()

        for (i in 0 until sheet.numMergedRegions) {
            val region = sheet.getMergedRegion(i)
            if (region.isInRange(row, col)) {
                val firstRow = region.firstRow
                val firstCol = region.firstColumn
                return formatter.formatCellValue(sheet.getRow(firstRow)?.getCell(firstCol)).trim()
            }
        }

        return formatter.formatCellValue(sheet.getRow(row)?.getCell(col)).trim()
    }
}

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
