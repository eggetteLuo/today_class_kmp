package com.eggetteluo.todayclass.database

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File

fun createDataStore(context: android.content.Context): DataStore<Preferences> {
    return PreferenceDataStoreFactory.create(
        produceFile = {
            File(context.filesDir, "datastore/settings.preferences_pb")
        }
    )
}