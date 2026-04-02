package com.eggetteluo.todayclass.database

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

fun createDataStore(context: android.content.Context): DataStore<Preferences> {
    return createDataStore(
        producePath = {
            context.filesDir.resolve(DATA_STORE_FILE_NAME).absolutePath
        }
    )
}
