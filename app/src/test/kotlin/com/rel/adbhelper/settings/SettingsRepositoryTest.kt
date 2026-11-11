package com.rel.adbhelper.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SettingsRepositoryTest {

    @org.junit.jupiter.api.Test
    fun `settings written are read back unchanged after a fresh repository instance`(@TempDir tempDir: File) =
        runTest {
            val dataStoreFile = File(tempDir, "settings.preferences_pb")
            val dataStore = PreferenceDataStoreFactory.create { dataStoreFile }

            SettingsRepository(dataStore).update(AppSettings(showHiddenFilesByDefault = true))
            val reread = SettingsRepository(dataStore).settings.first()

            assertTrue(reread.showHiddenFilesByDefault)
        }
}
