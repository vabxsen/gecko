package com.gecko.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gecko.core.database.entity.ConversationEntity
import com.gecko.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class GeckoDatabaseTest {

    @Test fun draftSurvivesClosingAndReopeningDatabase() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "draft-reopen-test.db"
        context.deleteDatabase(name)
        val draft = com.gecko.core.database.entity.DraftEntity("", "Unsent", "image", "notes.pdf", "[Page 2] Notes", 2)
        fun open() = Room.databaseBuilder(context, GeckoDatabase::class.java, name).allowMainThreadQueries().build()
        val first = open()
        try { first.draftDao().save(draft) } finally { first.close() }
        val second = open()
        try { assertEquals(draft, second.draftDao().get("")) }
        finally { second.close(); context.deleteDatabase(name) }
    }

    @Test fun draftsKeepTextAndAttachmentsSeparateBetweenChats() = runTest {
        val draft = com.gecko.core.database.entity.DraftEntity("chat", "Question", "image", "notes.pdf", "[Page 1] Notes", 1)
        database.draftDao().save(draft)
        database.draftDao().save(draft.copy(conversationId = "", text = "New chat"))
        assertEquals(draft, database.draftDao().get("chat"))
        database.draftDao().delete("chat")
        assertNull(database.draftDao().get("chat"))
        assertEquals("New chat", database.draftDao().get("")?.text)
        database.draftDao().deleteAll()
        assertNull(database.draftDao().get(""))
    }

    private lateinit var database: GeckoDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GeckoDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun conversation(id: String, title: String, updatedAt: Long = 0L) = ConversationEntity(
        id = id,
        title = title,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        pinned = false,
        providerId = null,
        modelId = null,
    )

    private fun message(id: String, conversationId: String, createdAt: Long) = MessageEntity(
        id = id,
        conversationId = conversationId,
        role = "USER",
        content = "hello",
        createdAt = createdAt,
        status = "COMPLETE",
        providerId = null,
        modelId = null,
        promptTokens = null,
        completionTokens = null,
        totalTokens = null,
        errorMessage = null,
    )

    @Test
    fun insertAndReadConversation() = runTest {
        database.conversationDao().upsert(conversation("c1", "First chat"))

        val loaded = database.conversationDao().getById("c1")

        assertEquals("First chat", loaded?.title)
    }

    @Test
    fun searchFiltersByTitle() = runTest {
        database.conversationDao().upsert(conversation("c1", "Trip planning"))
        database.conversationDao().upsert(conversation("c2", "Kotlin questions"))

        val results = database.conversationDao().search("kotlin").first()

        assertEquals(1, results.size)
        assertEquals("c2", results.first().id)
    }

    @Test
    fun deletingConversationCascadesToMessages() = runTest {
        database.conversationDao().upsert(conversation("c1", "Chat"))
        database.messageDao().upsert(message("m1", "c1", createdAt = 1L))
        database.messageDao().upsert(message("m2", "c1", createdAt = 2L))

        database.conversationDao().deleteById("c1")

        val remaining = database.messageDao().observeMessages("c1").first()
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun deleteAfterTruncatesTrailingMessages() = runTest {
        database.conversationDao().upsert(conversation("c1", "Chat"))
        database.messageDao().upsert(message("m1", "c1", createdAt = 1L))
        database.messageDao().upsert(message("m2", "c1", createdAt = 2L))
        database.messageDao().upsert(message("m3", "c1", createdAt = 3L))

        database.messageDao().deleteAfter("c1", afterCreatedAt = 1L)

        val remaining = database.messageDao().observeMessages("c1").first()
        assertEquals(listOf("m1"), remaining.map { it.id })
    }

    @Test
    fun renameUpdatesTitleAndTimestamp() = runTest {
        database.conversationDao().upsert(conversation("c1", "Old title", updatedAt = 1L))

        database.conversationDao().rename("c1", "New title", updatedAt = 2L)

        val loaded = database.conversationDao().getById("c1")
        assertEquals("New title", loaded?.title)
        assertEquals(2L, loaded?.updatedAt)
    }

    @Test
    fun deleteByIdRemovesConversation() = runTest {
        database.conversationDao().upsert(conversation("c1", "Chat"))

        database.conversationDao().deleteById("c1")

        assertNull(database.conversationDao().getById("c1"))
    }
}
