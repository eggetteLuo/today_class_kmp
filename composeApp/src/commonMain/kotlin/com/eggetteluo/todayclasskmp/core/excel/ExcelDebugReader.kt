package com.eggetteluo.todayclasskmp.core.excel

import com.eggetteluo.todayclasskmp.core.excel.model.CourseScheduleInstance
import io.github.vinceglb.filekit.core.PlatformFile

expect object ExcelDebugReader {
    suspend fun readAndLog(file: PlatformFile): List<CourseScheduleInstance>
}
