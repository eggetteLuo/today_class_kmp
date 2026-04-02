package com.eggetteluo.todayclass.util

import com.eggetteluo.todayclass.model.Course
import io.github.aakira.napier.Napier
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException
import org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException
import org.apache.poi.poifs.filesystem.OfficeXmlFileException
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream

actual class ExcelParser actual constructor() {
    actual fun parse(bytes: ByteArray): List<Course> {
        val courseList = mutableListOf<Course>()

        try {
            openWorkbook(bytes).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                val rowToSectionMap = mapOf(
                    4 to "1-2",
                    6 to "3-4",
                    8 to "5-6",
                    10 to "7-8",
                    12 to "9-10"
                )

                for ((rowIndex, defaultSection) in rowToSectionMap) {
                    sheet.getRow(rowIndex) ?: continue
                    for (colIndex in 1..7) {
                        val rawContent = getMergedCellValue(sheet, rowIndex, colIndex)
                        if (rawContent.isBlank()) continue

                        val parsed = DataCleaner.cleanRawText(
                            rawContent = rawContent,
                            dayOfWeek = colIndex,
                            defaultSection = defaultSection
                        )
                        courseList.addAll(parsed)
                    }
                }
            }
        } catch (e: Exception) {
            Napier.e(tag = "ExcelParser", throwable = e) { "Excel 解析失败" }
            throw IllegalArgumentException(
                "无法解析该 Excel 文件，请优先尝试另存为 .xlsx 后重新导入",
                e
            )
        }

        return courseList.distinctBy { "${it.name}-${it.dayOfWeek}-${it.section}-${it.originalWeeks}" }
    }

    private fun openWorkbook(bytes: ByteArray): Workbook {
        return try {
            when {
                bytes.isXls() -> HSSFWorkbook(ByteArrayInputStream(bytes))
                bytes.isXlsx() -> XSSFWorkbook(ByteArrayInputStream(bytes))
                else -> XSSFWorkbook(ByteArrayInputStream(bytes))
            }
        } catch (_: OLE2NotOfficeXmlFileException) {
            HSSFWorkbook(ByteArrayInputStream(bytes))
        } catch (_: OfficeXmlFileException) {
            XSSFWorkbook(ByteArrayInputStream(bytes))
        } catch (_: NotOfficeXmlFileException) {
            HSSFWorkbook(ByteArrayInputStream(bytes))
        }
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
