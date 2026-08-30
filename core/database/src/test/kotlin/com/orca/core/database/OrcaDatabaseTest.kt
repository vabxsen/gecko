package com.orca.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orca.core.database.entity.ConversationEntity
import com.orca.core.database.entity.MessageEntity
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
class OrcaDatabaseTest {

    private lateinit var database: OrcaDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OrcaDatabase::class.java,
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
