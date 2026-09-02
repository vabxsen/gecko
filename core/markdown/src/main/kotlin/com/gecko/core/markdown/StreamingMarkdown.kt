package com.gecko.core.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A partially-received markdown document, split into the part that can't change any more and the
 * part still being written.
 */
data class StreamingMarkdownSplit(
    /** Complete blocks. Grows only when a block closes, so it re-renders rarely. */
    val settled: String,
    /** The block currently being written. Changes constantly; rendered as plain text. */
    val pending: String,
)

/**
 * Splits [text] at the end of the last *complete* markdown block.
 *
 * Rendering markdown means parsing the whole document and rebuilding its entire composable tree —
 * including syntax-highlighting every code fence. Doing that for the full message on every token
 * is what makes a long reply stutter as it arrives, and it gets worse the longer the reply grows.
 *
 * A blank line at fence depth zero is the one place a markdown document is unambiguously
 * "finished so far": no later text can change how the blocks before it render. Everything up to
 * there is handed to the real renderer and then left alone, and only the trailing block — at most
 * a paragraph's worth — is re-rendered as each word lands.
 *
 * Blank lines *inside* a fenced code block don't count, or an unfinished ``` block would be split
 * in half and its opening fence rendered as a stray paragraph.
 */
fun splitStreamingMarkdown(text: String): StreamingMarkdownSplit {
    var inFence = false
    var settledEnd = 0
    var lineStart = 0

    while (lineStart <= text.length) {
        val newline = text.indexOf('\n', lineStart)
        val lineEnd = if (newline == -1) text.length else newline
        val line = text.substring(lineStart, lineEnd)
        val trimmed = line.trimStart()

        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inFence = !inFence
        } else if (!inFence && line.isBlank()) {
            settledEnd = (lineEnd + 1).coerceAtMost(text.length)
        }

        if (newline == -1) break
        lineStart = newline + 1
    }

    return StreamingMarkdownSplit(settled = text.take(settledEnd), pending = text.drop(settledEnd))
}

/**
 * Renders markdown that may still be arriving.
 *
 * Once [isStreaming] is false this is just [GeckoMarkdown]. While streaming it renders the
 * settled blocks with the full renderer and the in-flight block as plain text — see
 * [splitStreamingMarkdown]. The settled half keeps the same `String` value between updates, so
 * Compose skips it entirely and each new word only costs a plain-text relayout of the last
 * paragraph.
 *
 * The trade-off is that inline formatting in the paragraph being written (bold, links) stays
 * unstyled for the second or so until that paragraph closes. That's a far smaller cost than
 * dropping frames for the whole reply, and it resolves itself as the text lands.
 */
@Composable
fun StreamingMarkdown(
    content: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    // Both cases go through the same Column with the same two slots, deliberately. Swapping the
    // shape of the subtree at the moment a reply finishes makes the list re-measure the item from
    // scratch, which knocks the scroll position off the bottom just as the last words land.
    val split = remember(content, isStreaming) {
        if (isStreaming) splitStreamingMarkdown(content) else StreamingMarkdownSplit(content, "")
    }
    Column(modifier = modifier.fillMaxWidth()) {
        if (split.settled.isNotEmpty()) {
            GeckoMarkdown(content = split.settled)
        }
        if (split.pending.isNotBlank()) {
            PendingBlock(text = split.pending, topPadding = if (split.settled.isEmpty()) 0.dp else 8.dp)
        }
    }
}

/**
 * The block currently being written. It still goes through the markdown renderer — parsing one
 * trailing block per word is cheap and bounded, unlike re-parsing the whole reply — so headings,
 * bullets and emphasis are styled as they land rather than appearing as raw `##` and `**`.
 *
 * The exception is an unclosed code fence, which is both the most expensive thing to re-highlight
 * on every word and the one thing that should be shown verbatim anyway.
 */
@Composable
private fun PendingBlock(text: String, topPadding: Dp) {
    val trimmed = text.trimEnd()
    val isOpenFence = trimmed.trimStart().let { it.startsWith("```") || it.startsWith("~~~") }
    val modifier = Modifier.fillMaxWidth().padding(top = topPadding)

    if (isOpenFence) {
        Text(
            // Drop the opening fence line; the monospace-on-panel styling already says "code".
            text = trimmed.substringAfter('\n', missingDelimiterValue = ""),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    } else {
        GeckoMarkdown(content = trimmed.withoutDanglingEmphasis(), modifier = modifier)
    }
}

/** A run of emphasis characters at the very end of the text, e.g. the `**` of a half-typed bold. */
private val TRAILING_MARKER = Regex("""[*_`]+$""")

/**
 * Hides an emphasis marker that has been typed but not yet closed, so a bold word arriving
 * mid-stream doesn't flash a bare `**` on its own line before the rest of it lands. A *matched*
 * pair is left alone — stripping the closing `**` of a finished `**word**` would un-bold it.
 */
internal fun String.withoutDanglingEmphasis(): String {
    val match = TRAILING_MARKER.find(this) ?: return this
    val occurrences = Regex(Regex.escape(match.value)).findAll(this).count()
    return if (occurrences % 2 == 0) this else substring(0, match.range.first)
}
