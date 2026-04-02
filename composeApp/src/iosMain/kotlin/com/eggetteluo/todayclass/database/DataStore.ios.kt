package com.eggetteluo.todayclass.database

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

fun createDataStore(): DataStore<Preferences> {
    return createDataStore(
        producePath = {
            documentDirectory() + "/$DATA_STORE_FILE_NAME"
        }
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val directory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null
    )
    return requireNotNull(directory?.path) { "无法获取 iOS 文档目录" }
}
