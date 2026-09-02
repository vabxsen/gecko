package com.gecko.core.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingMarkdownTest {

    private fun split(text: String) = splitStreamingMarkdown(text)

    @Test
    fun aSingleUnfinishedParagraphIsAllPending() {
        val text = "Bridges are among humanity's most"
        assertEquals("", split(text).settled)
        assertEquals(text, split(text).pending)
    }

    @Test
    fun aClosedParagraphSettlesAndOnlyTheNewOneStaysPending() {
        val text = "# Bridges\n\nThey span things.\n\nThe first arch"
        val result = split(text)
        assertEquals("# Bridges\n\nThey span things.\n\n", result.settled)
        assertEquals("The first arch", result.pending)
    }

    @Test
    fun theSettledHalfIsStableAsTheNextParagraphGrows() {
        // This is the whole point: the expensive renderer must be handed the same String across
        // updates so Compose can skip it.
        val settled = split("Intro.\n\nSecond para").settled
        assertEquals(settled, split("Intro.\n\nSecond para is").settled)
        assertEquals(settled, split("Intro.\n\nSecond para is longer now").settled)
    }

    @Test
    fun blankLinesInsideAnOpenFenceDoNotSplitIt() {
        val text = "Here:\n\n```kotlin\nfun a() {}\n\nfun b() {}\n\nfun c"
        val result = split(text)
        assertEquals("Here:\n\n", result.settled)
        assertEquals("```kotlin\nfun a() {}\n\nfun b() {}\n\nfun c", result.pending)
    }

    @Test
    fun aClosedFenceCanSettleOnceABlankLineFollowsIt() {
        val text = "Here:\n\n```kotlin\nfun a() {}\n```\n\nAnd then"
        val result = split(text)
        assertEquals("Here:\n\n```kotlin\nfun a() {}\n```\n\n", result.settled)
        assertEquals("And then", result.pending)
    }

    @Test
    fun tildeFencesCountToo() {
        val text = "Intro.\n\n~~~\nraw\n\nstill raw"
        assertEquals("Intro.\n\n", split(text).settled)
    }

    @Test
    fun textEndingOnABlankLineIsEntirelySettled() {
        val result = split("All done.\n\n")
        assertEquals("All done.\n\n", result.settled)
        assertEquals("", result.pending)
    }

    @Test
    fun aHalfTypedEmphasisMarkerIsHiddenUntilItsWordArrives() {
        assertEquals("Bridges are ", "Bridges are **".withoutDanglingEmphasis())
        assertEquals("Then ", "Then `".withoutDanglingEmphasis())
    }

    @Test
    fun aMatchedEmphasisPairIsLeftAlone() {
        // Stripping the closing ** of a finished bold word would un-bold it.
        assertEquals("A **bold**", "A **bold**".withoutDanglingEmphasis())
        assertEquals("Use `code`", "Use `code`".withoutDanglingEmphasis())
        assertEquals("plain text", "plain text".withoutDanglingEmphasis())
    }

    @Test
    fun theTwoHalvesAlwaysReconstructTheOriginal() {
        listOf(
            "",
            "one",
            "one\n\ntwo",
            "# H\n\n- a\n- b\n\ntrailing",
            "```\nunclosed\n\nfence",
            "a\n\n\n\nb",
        ).forEach { text ->
            val result = split(text)
            assertEquals("round-trip failed for: $text", text, result.settled + result.pending)
        }
    }
}
