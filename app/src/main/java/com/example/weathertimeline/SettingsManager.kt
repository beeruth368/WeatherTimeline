package com.example.weathertimeline

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    private val API_KEY = stringPreferencesKey("ecowitt_api_key")
    private val APP_KEY = stringPreferencesKey("ecowitt_app_key")
    private val IS_METRIC = booleanPreferencesKey("is_metric")

    val apiKeyFlow: Flow<String?> = context.dataStore.data.map { it[API_KEY] }
    val appKeyFlow: Flow<String?> = context.dataStore.data.map { it[APP_KEY] }
    val isMetricFlow: Flow<Boolean> = context.dataStore.data.map { it[IS_METRIC] ?: true }

    suspend fun saveSettings(api: String, app: String, isMetric: Boolean) {
        context.dataStore.edit {
            it[API_KEY] = api
            it[APP_KEY] = app
            it[IS_METRIC] = isMetric
        }
    }
}