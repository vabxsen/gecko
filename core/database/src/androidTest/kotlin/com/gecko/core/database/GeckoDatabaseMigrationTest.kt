package com.gecko.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class GeckoDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GeckoDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migratesVersionOneWithoutLosingChatOrProviderMetadata() {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            execSQL("INSERT INTO conversations VALUES ('conversation-1', 'Saved chat', 1, 2, 0, 'openai', 'gpt-4o')")
            execSQL("INSERT INTO messages VALUES ('message-1', 'conversation-1', 'USER', 'hello', 1, 'COMPLETE', NULL, NULL, NULL, NULL, NULL, NULL, 'image-data')")
            execSQL("INSERT INTO provider_configs VALUES ('openai', 1, 'gpt-4o', NULL, 'SUCCESS', NULL)")
            execSQL("INSERT INTO model_catalog VALUES ('openai', 'gpt-4o', 'GPT-4o', 128000, 1, 1, 1)")
            close()
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 3, true, *GeckoDatabaseMigrations.ALL).use { database ->
            database.query("SELECT title FROM conversations WHERE id = 'conversation-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Saved chat", cursor.getString(0))
            }
            database.query("SELECT id, label FROM provider_configs WHERE providerId = 'openai'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("openai", cursor.getString(0))
                assertEquals("openai", cursor.getString(1))
            }
            database.query("SELECT configId FROM model_catalog WHERE modelId = 'gpt-4o'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("openai", cursor.getString(0))
            }
            database.query("SELECT generatedImageBase64 FROM messages WHERE id = 'message-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-test"
    }
}
