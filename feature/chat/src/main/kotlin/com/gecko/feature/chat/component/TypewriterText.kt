package com.gecko.feature.chat.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.ceil
import kotlinx.coroutines.delay

/**
 * How often the revealed text grows. A fixed cadence is the point: it caps how many times per
 * second the message re-lays-out no matter how fast the model is, so a fast reply costs the same
 * per second as a slow one. ~33ms is comfortably smoother than the eye needs for reading text
 * appear, at half the redraw cost of matching the display's frame rate.
 */
private const val TICK_MS = 33L

/**
 * How long the reveal aims to take to catch up with text it already holds, while the reply is
 * still arriving. Short enough that the words on screen track what the model has actually said;
 * long enough that they arrive one at a time rather than in slabs.
 */
private const val STREAMING_CATCH_UP_MS = 450f

/** The same target once the stream has finished — just tidy up what's left, briskly. */
private const val FINISHING_CATCH_UP_MS = 180f

/**
 * How far past the tick's character budget a single word may run. Normal words are far shorter
 * than this, so it never splits one; it exists so a base64 blob or a long minified code line —
 * one unbroken "word" — streams in rather than landing in a single jump.
 */
private const val MAX_WORD_OVERSHOOT = 120

/**
 * Reveals [fullText] a word at a time and returns the portion revealed so far.
 *
 * The network delivers tokens in bursts, and this app persists them on a 120ms throttle on top of
 * that, so binding the UI straight to the stored text makes a reply land in visible slabs. This
 * decouples the two: text is buffered as it arrives and played out at a steady, readable cadence,
 * which is what makes a reply look like it is being typed.
 *
 * The amount revealed per tick adapts to how far behind it is, so the reveal never becomes the
 * bottleneck — a fast model builds a backlog and each tick takes a proportionally bigger bite to
 * drain it, rather than falling further behind with every token. Once [isStreaming] goes false
 * the remainder is flushed on a much shorter target, so the reply settles promptly instead of
 * typing on after the model has finished.
 *
 * A message that is already complete when it first composes — anything in scrollback — is
 * returned whole and never animates.
 */
@Composable
fun rememberTypewriterText(fullText: String, isStreaming: Boolean): String {
    // Seeded from the *first* composition: a finished message must appear instantly, and only a
    // reply that was still streaming when its bubble first appeared should ever type itself out.
    var revealedCount by remember { mutableIntStateOf(if (isStreaming) 0 else fullText.length) }

    // Regenerating can replace the text with something shorter; never read past the end of it.
    val revealed = revealedCount.coerceAtMost(fullText.length)

    LaunchedEffect(fullText, isStreaming) {
        while (revealedCount < fullText.length) {
            delay(TICK_MS)
            val backlog = fullText.length - revealedCount
            val catchUpMs = if (isStreaming) STREAMING_CATCH_UP_MS else FINISHING_CATCH_UP_MS
            // Spread the whole backlog across the catch-up window: this tick's bite is its share
            // of the time remaining, so the wait per word shrinks as the backlog grows.
            val budget = ceil(backlog * TICK_MS / catchUpMs).toInt().coerceAtLeast(1)
            revealedCount = fullText.endOfWordAfter(revealedCount + budget, limit = revealedCount + budget + MAX_WORD_OVERSHOOT)
        }
    }

    return if (revealed >= fullText.length) fullText else fullText.take(revealed)
}

/**
 * The index just past the end of the word that [from] lands in or precedes, so the text always
 * grows by whole words. Never returns less than [from], and never runs past [limit].
 */
private fun String.endOfWordAfter(from: Int, limit: Int): Int {
    var index = from.coerceIn(0, length)
    while (index < length && this[index].isWhitespace()) index++
    while (index < length && !this[index].isWhitespace()) index++
    return index.coerceAtMost(maxOf(limit, from)).coerceAtMost(length)
}
