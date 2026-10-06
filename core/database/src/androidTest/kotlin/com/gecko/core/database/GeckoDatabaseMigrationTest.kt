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

    @Test
    fun versionSixKeepsExistingMessagesAndStoresDrafts() {
        helper.createDatabase(DATABASE_NAME, 5).apply {
            execSQL("INSERT INTO conversations VALUES ('chat', 'Keep me', 1, 2, 0, NULL, NULL)")
            execSQL("INSERT INTO messages (id, conversationId, role, content, createdAt, status) VALUES ('message', 'chat', 'USER', 'Existing text', 1, 'COMPLETE')")
            close()
        }
        helper.runMigrationsAndValidate(DATABASE_NAME, 6, true, *GeckoDatabaseMigrations.ALL).use { database ->
            database.query("SELECT content, documentName FROM messages WHERE id = 'message'").use {
                assertTrue(it.moveToFirst())
                assertEquals("Existing text", it.getString(0))
                assertTrue(it.isNull(1))
            }
            database.execSQL("INSERT INTO drafts VALUES ('chat', 'Unsent question', 'image', 'notes.pdf', '[Page 1] Notes', 1)")
            database.query("SELECT text, documentText FROM drafts WHERE conversationId = 'chat'").use {
                assertTrue(it.moveToFirst())
                assertEquals("Unsent question", it.getString(0))
                assertEquals("[Page 1] Notes", it.getString(1))
            }
        }
    }

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

        helper.runMigrationsAndValidate(DATABASE_NAME, 4, true, *GeckoDatabaseMigrations.ALL).use { database ->
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
            // v4's columns exist and are null on rows written before them — a message that
            // succeeded long ago must not come back looking like it failed.
            database.query("SELECT errorKind FROM messages WHERE id = 'message-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
            }
            database.query("SELECT connectionErrorKind FROM provider_configs WHERE id = 'openai'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
            }
        }
    }

    /**
     * The upgrade every existing install will actually take. v1 is the deep history; this is the
     * one that ships, and the one where a mistake loses someone's chats.
     */
    @Test
    fun migratesVersionThreeWithoutLosingChats() {
        helper.createDatabase(DATABASE_NAME, 3).apply {
            execSQL("INSERT INTO conversations VALUES ('conversation-1', 'Existing chat', 1, 2, 0, 'openai', 'gpt-4o')")
            execSQL(
                "INSERT INTO messages VALUES ('message-1', 'conversation-1', 'ASSISTANT', 'An answer', " +
                    "1, 'COMPLETE', 'openai', 'gpt-4o', NULL, NULL, NULL, NULL, NULL, NULL)",
            )
            execSQL(
                "INSERT INTO provider_configs VALUES ('config-1', 'openai', 'My key', 1, 'gpt-4o', " +
                    "NULL, 'SUCCESS', NULL, 0)",
            )
            close()
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 4, true, *GeckoDatabaseMigrations.ALL).use { database ->
            database.query("SELECT content, errorKind FROM messages WHERE id = 'message-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("An answer", cursor.getString(0))
                assertTrue(cursor.isNull(1))
            }
            // The saved key's row must survive intact: API keys are stored against this id, so
            // losing or renaming it orphans the key itself.
            database.query("SELECT label, connectionErrorKind FROM provider_configs WHERE id = 'config-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("My key", cursor.getString(0))
                assertTrue(cursor.isNull(1))
            }
        }
    }

    @Test
    fun migratesVersionFourWithoutTreatingCatalogSelectionsAsVerified() {
        helper.createDatabase(DATABASE_NAME, 4).apply {
            execSQL("INSERT INTO provider_configs VALUES ('saved-key', 'openai', 'My connection', 1, 'old-model', NULL, 'SUCCESS', NULL, NULL, 10)")
            execSQL("INSERT INTO conversations VALUES ('saved-chat', 'Keep this chat', 1, 2, 0, 'openai', 'old-model')")
            close()
        }
        helper.runMigrationsAndValidate(DATABASE_NAME, 5, true, *GeckoDatabaseMigrations.ALL).use { database ->
            database.query("SELECT id, label, selectedModelId, verifiedModelId, connectionStatus FROM provider_configs").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("saved-key", cursor.getString(0))
                assertEquals("My connection", cursor.getString(1))
                assertEquals("old-model", cursor.getString(2))
                assertTrue(cursor.isNull(3))
                assertEquals("UNTESTED", cursor.getString(4))
            }
            database.query("SELECT title FROM conversations WHERE id = 'saved-chat'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Keep this chat", cursor.getString(0))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-test"
    }
}
