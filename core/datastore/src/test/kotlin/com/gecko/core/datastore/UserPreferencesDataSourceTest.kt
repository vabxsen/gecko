package com.gecko.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gecko.core.model.preferences.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class UserPreferencesDataSourceTest {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun changingSelectionNeverEmitsAMismatchedProviderAndModel() = runTest {
        dataSource.setDefaultSelection("openai-key", "gpt-4o")
        val observed = mutableListOf<Pair<String?, String?>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            dataSource.userPreferences.collect { observed += it.defaultProviderConfigId to it.defaultModelId }
        }

        dataSource.setDefaultSelection("gemini-key", "gemini-flash")
        val final = dataSource.userPreferences.first()

        assertEquals("gemini-key", final.defaultProviderConfigId)
        assertEquals("gemini-flash", final.defaultModelId)
        org.junit.Assert.assertTrue(observed.all {
            it == ("openai-key" to "gpt-4o") || it == ("gemini-key" to "gemini-flash")
        })
    }

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var dataSource: UserPreferencesDataSource

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tmpFolder.newFile("prefs_${UUID.randomUUID()}.preferences_pb") },
        )
        dataSource = UserPreferencesDataSource(context, dataStore)
    }

    @Test
    fun defaultsAreSensible() = runTest {
        val prefs = dataSource.userPreferences.first()

        assertEquals(ThemeMode.SYSTEM, prefs.themeMode)
        assertFalse(prefs.dynamicColorEnabled)
        assertNull(prefs.defaultProviderConfigId)
        assertTrueSendOnEnterDefault(prefs.sendOnEnter)
    }

    private fun assertTrueSendOnEnterDefault(value: Boolean) = assertEquals(true, value)

    @Test
    fun settingThemeModePersists() = runTest {
        dataSource.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, dataSource.userPreferences.first().themeMode)
    }

    @Test
    fun settingDefaultProviderPersists() = runTest {
        dataSource.setDefaultProviderConfig("config-1")

        assertEquals("config-1", dataSource.userPreferences.first().defaultProviderConfigId)
    }

    @Test
    fun clearingDefaultProviderRemovesIt() = runTest {
        dataSource.setDefaultProviderConfig("config-1")
        dataSource.setDefaultProviderConfig(null)

        assertNull(dataSource.userPreferences.first().defaultProviderConfigId)
    }

    @Test
    fun clearAllResetsToDefaults() = runTest {
        dataSource.setThemeMode(ThemeMode.LIGHT)
        dataSource.setStreamingEnabled(false)

        dataSource.clearAll()

        val prefs = dataSource.userPreferences.first()
        assertEquals(ThemeMode.SYSTEM, prefs.themeMode)
        assertEquals(true, prefs.streamingEnabled)
    }
}
