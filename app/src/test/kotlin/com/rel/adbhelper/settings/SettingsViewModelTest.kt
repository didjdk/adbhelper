package com.rel.adbhelper.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * DataStore does its own file I/O on a real dispatcher, outside the test coroutine scheduler's
 * virtual time — these tests await the actual expected value from the flow via a plain
 * suspending `first { predicate }` rather than reading `StateFlow.value` synchronously (which
 * would race the real I/O) or wrapping in `withTimeout` (whose own `delay` runs on virtual time
 * and can fire before the real I/O callback does).
 */
class SettingsViewModelTest {

    @Test
    fun `starts with the current persisted settings, not always the hardcoded default`(@TempDir tempDir: File) =
        runTest {
            val dataStore = PreferenceDataStoreFactory.create { File(tempDir, "settings.preferences_pb") }
            SettingsRepository(dataStore).update(AppSettings(screenshotDirectory = "/sdcard/Existing"))

            val vm = SettingsViewModel(SettingsRepository(dataStore))
            val settings = vm.settings.first { it.screenshotDirectory == "/sdcard/Existing" }
            assertEquals("/sdcard/Existing", settings.screenshotDirectory)
        }

    @Test
    fun `onShowHiddenFilesByDefaultChanged persists and updates state`(@TempDir tempDir: File) = runTest {
        val dataStore = PreferenceDataStoreFactory.create { File(tempDir, "settings.preferences_pb") }
        val vm = SettingsViewModel(SettingsRepository(dataStore))

        vm.onShowHiddenFilesByDefaultChanged(true)

        val settings = vm.settings.first { it.showHiddenFilesByDefault }
        assertEquals(true, settings.showHiddenFilesByDefault)
    }

    @Test
    fun `onScreenshotDirectoryChanged persists and updates state`(@TempDir tempDir: File) = runTest {
        val dataStore = PreferenceDataStoreFactory.create { File(tempDir, "settings.preferences_pb") }
        val vm = SettingsViewModel(SettingsRepository(dataStore))

        vm.onScreenshotDirectoryChanged("/sdcard/NewDir")

        val settings = vm.settings.first { it.screenshotDirectory == "/sdcard/NewDir" }
        assertEquals("/sdcard/NewDir", settings.screenshotDirectory)
    }

    @Test
    fun `onDefaultPostConnectScreenChanged persists and updates state`(@TempDir tempDir: File) = runTest {
        val dataStore = PreferenceDataStoreFactory.create { File(tempDir, "settings.preferences_pb") }
        val vm = SettingsViewModel(SettingsRepository(dataStore))

        vm.onDefaultPostConnectScreenChanged("file_manager")

        val settings = vm.settings.first { it.defaultPostConnectScreen == "file_manager" }
        assertEquals("file_manager", settings.defaultPostConnectScreen)
    }

    @Test
    fun `changing one field does not clobber the others`(@TempDir tempDir: File) = runTest {
        val dataStore = PreferenceDataStoreFactory.create { File(tempDir, "settings.preferences_pb") }
        val vm = SettingsViewModel(SettingsRepository(dataStore))

        vm.onScreenshotDirectoryChanged("/sdcard/NewDir")
        vm.settings.first { it.screenshotDirectory == "/sdcard/NewDir" }

        vm.onShowHiddenFilesByDefaultChanged(true)
        val settings = vm.settings.first { it.showHiddenFilesByDefault }

        assertEquals("/sdcard/NewDir", settings.screenshotDirectory)
        assertEquals(true, settings.showHiddenFilesByDefault)
    }
}
