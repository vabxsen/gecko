package com.gecko.domain.model

import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId

/**
 * [primary] is what a picker should show by default; [remainder] is everything else, reachable
 * behind a "Show all models" toggle so nothing a provider returns is ever permanently hidden.
 */
data class CuratedModels(
    val primary: List<ModelInfo>,
    val remainder: List<ModelInfo>,
) {
    val hasMore: Boolean get() = remainder.isNotEmpty()
}

/**
 * Google's `/models` endpoint returns every product riding the `generateContent` method, not
 * just chat models: TTS, image generation, robotics, music, agent/research tools, and every
 * dated preview snapshot. [GEMINI_STABLE_MODEL_ID] is an allowlist (not a keyword blacklist) for
 * a clean `gemini-{optional version}-{pro|flash|flash-lite}` or `gemini-{tier}-latest` id, so it
 * keeps working as Google ships new non-chat product lines instead of needing a new exclusion
 * every time.
 */
private val GEMINI_STABLE_MODEL_ID = Regex("""^gemini-(\d+(?:\.\d+)?-)?(pro|flash-lite|flash)(-latest)?$""")

/**
 * OpenAI-compatible `/models` catalogs (used for NVIDIA NIM, DeepSeek, Kimi, and OpenAI itself)
 * mix chat models in with embeddings, rerankers, speech, and safety/moderation models, and unlike
 * Gemini's product line there's no single clean id pattern to allowlist against across vendors.
 * This is a denylist instead: deliberately conservative, since leaving a stray non-chat model in
 * [CuratedModels.primary] is a much smaller problem than hiding a real chat model behind "Show
 * all". Keyword match, not id-anchored, so it also catches vendor-prefixed variants.
 */
private val NON_CHAT_MODEL_KEYWORD = Regex(
    """embed|rerank|whisper|\btts\b|speech|guard|moderation|safety|\bclip\b|\bada\b|davinci-instruct|dall-e""",
    RegexOption.IGNORE_CASE,
)

fun List<ModelInfo>.curatedForSelection(providerId: ProviderId): CuratedModels = when (providerId) {
    ProviderId.GOOGLE -> {
        val (primary, remainder) = partition { GEMINI_STABLE_MODEL_ID.matches(it.modelId) }
        CuratedModels(primary = primary.sortedWith(geminiModelOrder), remainder = remainder)
    }
    ProviderId.OPENAI -> {
        val (remainder, primary) = partition { NON_CHAT_MODEL_KEYWORD.containsMatchIn(it.modelId) }
        CuratedModels(primary = primary, remainder = remainder)
    }
    else -> CuratedModels(primary = this, remainder = emptyList())
}

private val geminiModelOrder = compareBy<ModelInfo>(
    { !it.isGeminiLatestAlias() },
    { -it.geminiVersionOrZero() },
    { it.geminiTierRank() },
)

private fun ModelInfo.geminiMatch() = GEMINI_STABLE_MODEL_ID.find(modelId)

private fun ModelInfo.isGeminiLatestAlias(): Boolean = geminiMatch()?.groupValues?.get(3)?.isNotEmpty() == true

private fun ModelInfo.geminiVersionOrZero(): Double =
    geminiMatch()?.groupValues?.get(1)?.trimEnd('-')?.toDoubleOrNull() ?: 0.0

private fun ModelInfo.geminiTierRank(): Int = when (geminiMatch()?.groupValues?.get(2)) {
    "pro" -> 0
    "flash" -> 1
    "flash-lite" -> 2
    else -> 3
}
