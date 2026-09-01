package com.gecko.domain.model

import com.gecko.core.model.chat.ChatMessage
import com.gecko.core.model.chat.MessageRole
import kotlin.math.ceil
import kotlin.math.max

/** Rough client-side token estimate absent a bundled per-vendor tokenizer. */
private const val CHARS_PER_TOKEN = 4.0

/** A flat estimate for an attached/generated image, since its real cost varies by provider/size. */
private const val IMAGE_TOKEN_ESTIMATE = 1200

/** Always reserve at least this many tokens of the context window for the model's response. */
private const val MIN_RESPONSE_RESERVE_TOKENS = 2048

/**
 * Sends the full [ChatMessage] history unconditionally would eventually exceed a model's context
 * window on a long conversation, producing a hard 400 from the provider with no client-side
 * recourse. This trims the **oldest non-system messages first** — never splitting a message,
 * never dropping a system message, never dropping the most recent user message — until the
 * estimated token count fits inside [contextWindowTokens] minus a reserved response budget.
 *
 * [contextWindowTokens] of `null` means the limit is unknown (e.g. a provider that doesn't
 * report one) — the history is returned unchanged rather than guessing.
 */
fun List<ChatMessage>.trimToContextBudget(contextWindowTokens: Int?): List<ChatMessage> {
    if (contextWindowTokens == null || contextWindowTokens <= 0) return this
    val responseReserve = max(MIN_RESPONSE_RESERVE_TOKENS, contextWindowTokens / 8)
    val budget = contextWindowTokens - responseReserve
    if (budget <= 0) return this

    var total = sumOf { it.estimatedTokens() }
    if (total <= budget) return this

    val trimmed = toMutableList()
    var index = 0
    while (total > budget && index < trimmed.size) {
        val candidate = trimmed[index]
        val isLastUserMessage = candidate.role == MessageRole.USER &&
            trimmed.drop(index + 1).none { it.role == MessageRole.USER }
        if (candidate.role == MessageRole.SYSTEM || isLastUserMessage) {
            index++
            continue
        }
        total -= candidate.estimatedTokens()
        trimmed.removeAt(index)
    }
    return trimmed
}

private fun ChatMessage.estimatedTokens(): Int {
    val textTokens = ceil(content.length / CHARS_PER_TOKEN).toInt()
    val imageTokens = if (attachmentImageBase64 != null || generatedImageBase64 != null) IMAGE_TOKEN_ESTIMATE else 0
    return textTokens + imageTokens
}
