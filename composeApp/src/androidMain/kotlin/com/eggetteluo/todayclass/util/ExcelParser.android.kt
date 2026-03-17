package com.eggetteluo.todayclass.util

import com.eggetteluo.todayclass.model.Course
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream

actual class ExcelParser actual constructor() {
    actual fun parse(bytes: ByteArray): List<Course> {
        val courseList = mutableListOf<Course>()

        try {
            // 1. 加载工作簿
            val workbook = WorkbookFactory.create(ByteArrayInputStream(bytes))
            val sheet = workbook.getSheetAt(0)

            // 2. 扫描关键行（第一、三、五、七、九节）
            // 对应 Excel 的 Row 索引通常是 4, 6, 8, 10, 12
            val targetRows = listOf(4, 6, 8, 10, 12)

            for (rowIndex in targetRows) {
                val row = sheet.getRow(rowIndex) ?: continue

                // 获取节次标签（如 "第一节"）
                val sectionLabel = row.getCell(0)?.toString()?.trim() ?: "未知节次"

                // 3. 扫描周一到周日（Column 1 到 7）
                for (colIndex in 1..7) {
                    val cell = row.getCell(colIndex) ?: continue
                    val rawContent = cell.toString().trim()

                    if (rawContent.isNotEmpty()) {
                        // 4. 调用 Kotlin 通用正则清洗逻辑
                        val parsed = DataCleaner.cleanRawText(
                            rawContent = rawContent,
                            dayOfWeek = colIndex,
                            section = sectionLabel
                        )
                        courseList.addAll(parsed)
                    }
                }
            }
            workbook.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return courseList
    }
}