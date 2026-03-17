package com.eggetteluo.todayclass.util

import com.eggetteluo.todayclass.model.Course

expect class ExcelParser() {

    /**
     * 解析字节数组并打印内容
     */
    fun parse(bytes: ByteArray): List<Course>

}