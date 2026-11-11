package com.rel.adbhelper.core.recents

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.recentsDataStore by preferencesDataStore(name = "recents")

/** Recently connected `host:port` targets, persisted, each removable. */
class RecentConnections(context: Context) {

    private val dataStore = context.applicationContext.recentsDataStore

    val entries: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[KEY]?.sorted().orEmpty()
    }

    suspend fun add(entry: String) {
        dataStore.edit { it[KEY] = (it[KEY] ?: emptySet()) + entry }
    }

    suspend fun remove(entry: String) {
        dataStore.edit { it[KEY] = (it[KEY] ?: emptySet()) - entry }
    }

    companion object {
        private val KEY = stringSetPreferencesKey("recent_connections")
    }
}
