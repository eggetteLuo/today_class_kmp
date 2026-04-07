package com.eggetteluo.todayclass.core.excel

import com.eggetteluo.todayclass.core.excel.model.CourseScheduleInstance
import io.github.vinceglb.filekit.core.PlatformFile

expect object ExcelDebugReader {
    suspend fun readAndLog(file: PlatformFile): List<CourseScheduleInstance>
}
