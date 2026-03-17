package com.eggetteluo.todayclass.util

import com.eggetteluo.todayclass.model.Course
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes

actual class ExcelParser actual constructor() {
    @OptIn(ExperimentalForeignApi::class)
    actual fun parse(bytes: ByteArray): List<Course> {
        // 1. 将 ByteArray 转为 iOS 原生的 NSData
        val nsData = bytes.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
        }

        // 2. iOS 端的 XLSX 其实是一个压缩包
        // 在 iOS 上，我们通常会调用 Swift 编写的工具类来处理
        // 这里先打印基础信息，确保调用链是通的
        Napier.d(tag = "ExcelParser") { "--- iOS 原生层收到数据 ---" }
        Napier.d(tag = "ExcelParser") { "文件大小: ${nsData.length / 1024u} KB" }

        return emptyList()
    }
}