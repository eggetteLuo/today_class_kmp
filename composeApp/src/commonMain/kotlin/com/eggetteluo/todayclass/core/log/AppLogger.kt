package com.eggetteluo.todayclass.core.log

import co.touchlab.kermit.Logger

object AppLogger {
    fun d(tag: String, message: String) {
        Logger.withTag(tag).d { message }
    }

    fun i(tag: String, message: String) {
        Logger.withTag(tag).i { message }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Logger.withTag(tag).e(throwable) { message }
    }
}
