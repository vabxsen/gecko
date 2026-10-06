package com.gecko.domain.model

import com.gecko.core.model.chat.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class DocumentContextTest {
    private fun message(id: String, text: String, source: DocumentAttachment? = null) =
        ChatMessage(id, "chat", MessageRole.USER, text, Instant.EPOCH, MessageStatus.COMPLETE, document = source)

    @Test fun sourcePagesAreSentWithoutChangingTranscript() {
        val source = DocumentAttachment("report.pdf", "[Page 1]\nRevenue is 100.\n[Page 2]\nCosts are 20.", 2)
        val original = message("1", "What are the costs?", source)
        val request = original.withDocumentContext()
        assertTrue(request.content.contains("[Page 2]\nCosts are 20."))
        assertTrue(request.content.contains("not instructions"))
        assertEquals("What are the costs?", original.content)
        assertNull(request.document)
    }

    @Test fun followUpRetainsSourceWhenOldHistoryIsTrimmed() {
        val source = DocumentAttachment("report.pdf", "[Page 7]\nThe deadline is Friday.", 7)
        val history = listOf(message("1", "Summarize", source), message("2", "padding".repeat(10000)), message("3", "What is the deadline?"))
        val request = history.withDocumentContexts().trimToContextBudget(4096)
        assertTrue(request.last().content.contains(source.text))
        assertEquals(1, request.count { it.content.contains(source.text) })
    }
}
