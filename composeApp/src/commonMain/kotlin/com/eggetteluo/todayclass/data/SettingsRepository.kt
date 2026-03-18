package com.eggetteluo.todayclass.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    private val START_DATE_KEY = stringPreferencesKey("semester_start_date")

    val startDateFlow: Flow<String?> = dataStore.data.map { it[START_DATE_KEY] }

    suspend fun saveStartDate(date: String) {
        dataStore.edit { it[START_DATE_KEY] = date }
    }
}