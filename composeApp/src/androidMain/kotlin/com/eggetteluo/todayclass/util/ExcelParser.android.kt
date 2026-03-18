package com.eggetteluo.todayclass.util

import com.eggetteluo.todayclass.model.Course
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.util.CellRangeAddress
import java.io.ByteArrayInputStream

actual class ExcelParser actual constructor() {
    actual fun parse(bytes: ByteArray): List<Course> {
        val courseList = mutableListOf<Course>()
        try {
            val workbook = WorkbookFactory.create(ByteArrayInputStream(bytes))
            val sheet = workbook.getSheetAt(0)

            val rowToSectionMap = mapOf(
                4 to "1-2", 6 to "3-4", 8 to "5-6", 10 to "7-8", 12 to "9-10"
            )

            for ((rowIndex, defaultSection) in rowToSectionMap) {
                sheet.getRow(rowIndex) ?: continue
                for (colIndex in 1..7) {
                    // 获取单元格内容，支持合并单元格取值
                    val rawContent = getMergedCellValue(sheet, rowIndex, colIndex)

                    if (rawContent.isNotEmpty()) {
                        val parsed = DataCleaner.cleanRawText(
                            rawContent = rawContent,
                            dayOfWeek = colIndex,
                            defaultSection = defaultSection
                        )
                        courseList.addAll(parsed)
                    }
                }
            }
            workbook.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // 使用 distinctBy 避免 5-8 节课因为扫描两次而产生重复
        return courseList.distinctBy { "${it.name}-${it.dayOfWeek}-${it.section}-${it.originalWeeks}" }
    }

    /**
     * 获取单元格内容：如果是合并单元格，则返回左上角首格的内容
     */
    private fun getMergedCellValue(sheet: Sheet, row: Int, col: Int): String {
        for (i in 0 until sheet.numMergedRegions) {
            val region = sheet.getMergedRegion(i)
            if (region.isInRange(row, col)) {
                val firstRow = region.firstRow
                val firstCol = region.firstColumn
                return sheet.getRow(firstRow)?.getCell(firstCol)?.toString()?.trim() ?: ""
            }
        }
        return sheet.getRow(row)?.getCell(col)?.toString()?.trim() ?: ""
    }
}