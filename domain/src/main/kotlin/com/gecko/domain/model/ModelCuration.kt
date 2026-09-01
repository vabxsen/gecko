package com.gecko.domain.model

import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId

/**
 * [primary] is the reliable shortlist for normal use; [remainder] is always structurally
 * reachable (e.g. behind a "Show all" toggle) so a provider's real catalog is never fully
 * hidden. [curatedAllowlistOnly] is purely advisory: it tells a *compact* picker (the in-chat
 * dropdown) that this provider has a small, maintained allowlist and it's fine to omit the
 * "Show all" affordance there — a fuller picker (Settings) should ignore it and always offer
 * [remainder] as an advanced escape hatch.
 */
data class CuratedModels(
    val primary: List<ModelInfo>,
    val remainder: List<ModelInfo>,
    val curatedAllowlistOnly: Boolean = false,
) {
    val hasMore: Boolean get() = remainder.isNotEmpty()
}

/**
 * Google's `/models` endpoint returns every product riding the `generateContent` method, not
 * just chat models: TTS, image generation, robotics, music, agent/research tools, and every
 * dated preview snapshot. The moving `-latest` aliases can land on a preview tier with a much
 * tighter rate limit than the dated stable releases, so this prefers two specific dated Flash
 * versions over the Flash alias (each with its own fallback chain for accounts that don't have
 * that exact dated id yet).
 */
private val GEMINI_PREFERRED_MODELS = listOf(
    listOf("gemini-pro-latest", "gemini-2.5-pro"),
    listOf("gemini-3.6-flash", "gemini-flash-latest", "gemini-2.5-flash"),
    listOf("gemini-3.5-flash", "gemini-flash-latest", "gemini-2.5-flash", "gemini-2.5-flash-lite"),
)

private val NVIDIA_NIM_PREFERRED_MODELS = listOf(
    "nvidia/nemotron-3.5-lightning-30b-a3b",
    "nvidia/nemotron-3-super-120b-a12b",
    "nvidia/nemotron-3-nano-30b-a3b",
)

private const val NVIDIA_NIM_HOST = "integrate.api.nvidia.com"

/**
 * OpenAI-compatible `/models` catalogs (used for NVIDIA NIM, DeepSeek, Kimi, and OpenAI itself)
 * mix chat models in with embeddings, rerankers, speech, and safety/moderation models, and unlike
 * Gemini's product line there's no single clean id pattern to allowlist against across vendors.
 * This is a denylist instead: deliberately conservative, since leaving a stray non-chat model in
 * [CuratedModels.primary] is a much smaller problem than hiding a real chat model behind "Show
 * all". Keyword match, not id-anchored, so it also catches vendor-prefixed variants.
 */
private val NON_CHAT_MODEL_KEYWORD = Regex(
    """embed|rerank|whisper|\btts\b|speech|transcribe|realtime|guard|moderation|safety|\bclip\b|\bada\b""" +
        """|davinci|babbage|turbo-instruct|computer-use|dall-e|gpt-image""",
    RegexOption.IGNORE_CASE,
)

fun List<ModelInfo>.curatedForSelection(
    providerId: ProviderId,
    baseUrlOverride: String? = null,
): CuratedModels = when {
    baseUrlOverride?.contains(NVIDIA_NIM_HOST, ignoreCase = true) == true -> curatedNvidiaNimModels()
    providerId == ProviderId.GOOGLE -> curatedGeminiModels()
    providerId == ProviderId.OPENAI -> {
        val (remainder, primary) = partition { NON_CHAT_MODEL_KEYWORD.containsMatchIn(it.modelId) }
        CuratedModels(primary = primary, remainder = remainder)
    }
    else -> CuratedModels(primary = this, remainder = emptyList())
}

private fun List<ModelInfo>.curatedGeminiModels(): CuratedModels {
    val byId = associateBy { it.modelId }
    // Tiers' fallback chains can overlap (two tiers both fall back to the same alias when their
    // preferred dated id is missing) — track what's already been picked so the same model never
    // fills two slots at once.
    val picked = LinkedHashSet<String>()
    val primary = GEMINI_PREFERRED_MODELS.mapNotNull { candidates ->
        candidates.firstNotNullOfOrNull { id -> byId[id]?.takeIf { it.modelId !in picked } }
            ?.also { picked += it.modelId }
    }
    // None of the preferred aliases/fallbacks are in this catalog (stale allowlist, unusual
    // region/tier) — degrade to the raw catalog rather than stranding the user with zero models.
    if (primary.isEmpty()) return CuratedModels(primary = this, remainder = emptyList())
    return CuratedModels(
        primary = primary,
        remainder = filterNot { it.modelId in picked },
        curatedAllowlistOnly = true,
    )
}

private fun List<ModelInfo>.curatedNvidiaNimModels(): CuratedModels {
    val byId = associateBy { it.modelId }
    val primary = NVIDIA_NIM_PREFERRED_MODELS.mapNotNull(byId::get)
    if (primary.isEmpty()) return CuratedModels(primary = this, remainder = emptyList())
    return CuratedModels(
        primary = primary,
        remainder = filterNot { it.modelId in NVIDIA_NIM_PREFERRED_MODELS },
        curatedAllowlistOnly = true,
    )
}
