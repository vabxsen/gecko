package com.gecko.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gecko.core.model.preferences.ThemeMode
import com.gecko.core.model.provider.ProviderId
import kotlinx.coroutines.flow.first
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
        assertNull(prefs.defaultProviderId)
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
        dataSource.setDefaultProvider(ProviderId.ANTHROPIC)

        assertEquals(ProviderId.ANTHROPIC, dataSource.userPreferences.first().defaultProviderId)
    }

    @Test
    fun clearingDefaultProviderRemovesIt() = runTest {
        dataSource.setDefaultProvider(ProviderId.OPENAI)
        dataSource.setDefaultProvider(null)

        assertNull(dataSource.userPreferences.first().defaultProviderId)
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
