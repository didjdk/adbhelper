package com.rel.adbhelper.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val KEY_DEFAULT_POST_CONNECT_SCREEN = stringPreferencesKey("default_post_connect_screen")
private val KEY_SHOW_HIDDEN_FILES_BY_DEFAULT = booleanPreferencesKey("show_hidden_files_by_default")
private val KEY_SCREENSHOT_DIRECTORY = stringPreferencesKey("screenshot_directory")

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        val defaults = AppSettings()
        AppSettings(
            defaultPostConnectScreen = preferences[KEY_DEFAULT_POST_CONNECT_SCREEN]
                ?: defaults.defaultPostConnectScreen,
            showHiddenFilesByDefault = preferences[KEY_SHOW_HIDDEN_FILES_BY_DEFAULT]
                ?: defaults.showHiddenFilesByDefault,
            screenshotDirectory = preferences[KEY_SCREENSHOT_DIRECTORY]
                ?: defaults.screenshotDirectory,
        )
    }

    suspend fun update(settings: AppSettings) {
        dataStore.edit { preferences ->
            preferences[KEY_DEFAULT_POST_CONNECT_SCREEN] = settings.defaultPostConnectScreen
            preferences[KEY_SHOW_HIDDEN_FILES_BY_DEFAULT] = settings.showHiddenFilesByDefault
            preferences[KEY_SCREENSHOT_DIRECTORY] = settings.screenshotDirectory
        }
    }
}
