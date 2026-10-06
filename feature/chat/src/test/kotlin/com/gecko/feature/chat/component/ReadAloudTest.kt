package com.gecko.feature.chat.component

import org.junit.Assert.*
import org.junit.Test

class ReadAloudTest {
    @Test fun longResponsesAreCompleteAndWithinEngineLimit() {
        val text = "A long answer with words. ".repeat(1000).trim()
        val chunks = speechChunks(text, 120)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 120 })
    }
    @Test fun doesNotSplitEmojiSurrogates() {
        val chunks = speechChunks("123😀456😀789", 4)
        assertEquals("123😀456😀789", chunks.joinToString(""))
        assertTrue(chunks.none { it.last().isHighSurrogate() || it.first().isLowSurrogate() })
    }
    @Test fun removesMarkdownAndSkipsCodeBlocks() {
        assertEquals("Title\nHello world.  Code block omitted.",
            speechChunks("# Title\n**Hello** [world](https://example.com). ```secret code```", 200).single())
        assertTrue(speechChunks("   ", 200).isEmpty())
    }
}
