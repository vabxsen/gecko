package com.gecko.domain.model

import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderFlavor
import com.gecko.core.model.provider.ProviderId

/**
 * [primary] is the short list a newcomer chooses from; [remainder] is always structurally
 * reachable behind a "Show all" toggle, so a provider's real catalog is never hidden — just not
 * the first thing anyone has to read.
 */
data class CuratedModels(
    val primary: List<ModelInfo>,
    val remainder: List<ModelInfo>,
) {
    val hasMore: Boolean get() = remainder.isNotEmpty()

    /**
     * The model to adopt on the user's behalf when they haven't chosen one.
     *
     * Not simply the first entry. [primary] leads with the vendor's flagship because that's the
     * natural way to read a list, but flagships are exactly what free tiers meter hardest —
     * measured against a real free Gemini key, the top tier answered every request with a 429
     * while the quick tier worked fine. Handing a new user a model that refuses to reply is the
     * failure this whole exercise is about, so the everyday tier is the safer thing to start on
     * and the flagship stays one tap away.
     */
    val defaultChoice: ModelInfo?
        get() = primary.firstOrNull { it.trait == ModelTrait.Fast } ?: primary.firstOrNull()
}

/**
 * The most models any provider may lead with. A hard number rather than a per-provider judgement
 * call: the failure this exists to prevent — a catalog arriving unfiltered — is the same size of
 * problem whether it's OpenRouter's four hundred models or OpenAI's fifty.
 */
private const val PRIMARY_HARD_CAP = 6

/**
 * Below this many resolved slots the shortlist is treated as stale rather than short. One
 * surviving tier is nearly as unhelpful as none, and a vendor renaming its line-up is exactly what
 * produces it.
 */
private const val MIN_TRUSTWORTHY_SHORTLIST = 2

/**
 * Models that ride the same catalogs as chat models but can't hold a conversation: embeddings,
 * rerankers, speech, safety classifiers, image and video generation, Google's robotics and music
 * products. Keyword-matched rather than id-anchored so vendor-prefixed variants are caught too.
 *
 * Getting this wrong in one direction hides a real chat model behind "Show all"; in the other it
 * lets the auto-selector pick a text-to-speech model and leaves the user with an app that appears
 * broken. That asymmetry is why the ranked fallback filters on this unconditionally.
 */
private val NON_CHAT_MODEL_KEYWORD = Regex(
    """embed|rerank|whisper|\btts\b|speech|transcribe|realtime|guard|moderation|safety|\bclip\b|\bada\b""" +
        """|davinci|babbage|turbo-instruct|computer-use|dall-e|gpt-image|imagen|\bveo\b|lyria|robotics""" +
        """|antigravity|deep-research|native-audio|-live-|\bocr\b|\bnvclip\b|banana""",
    RegexOption.IGNORE_CASE,
)

/** Snapshots and trial builds: real chat models, but not what anyone should be handed by default. */
private val PROVISIONAL_MODEL = Regex(
    """preview|experimental|-exp\b|\d{4}-\d{2}-\d{2}|-\d{8}\b""",
    RegexOption.IGNORE_CASE,
)

/** The first version number in an id — `gpt-5.1` reads as 5, `claude-opus-4-5` as 4. */
private val LEADING_VERSION = Regex("""\d+""")

/**
 * Splits a provider's catalog into the shortlist to lead with and everything else.
 *
 * The previous version curated only Google and NVIDIA NIM. OpenAI got a denylist that still left
 * forty-odd models in [primary]; Anthropic and OpenRouter got nothing at all, and because their
 * [remainder] came back empty there wasn't even a "Show all" toggle to collapse them behind —
 * OpenRouter's several hundred models were rendered in full, every time. Worse, both curated
 * providers fell back to the *entire raw catalog* whenever their allowlist stopped matching, so a
 * vendor rename turned a three-model list into a flood, silently.
 *
 * Now every provider gets a shortlist or a ranked substitute, and [primary] is capped either way.
 */
fun List<ModelInfo>.curatedForSelection(
    providerId: ProviderId,
    baseUrlOverride: String? = null,
): CuratedModels {
    if (isEmpty()) return CuratedModels(emptyList(), emptyList())

    val flavor = ProviderFlavor.of(providerId, baseUrlOverride)
    val shortlisted = SHORTLISTS[flavor].orEmpty().resolveAgainst(this)
    val primary = if (shortlisted.size >= MIN_TRUSTWORTHY_SHORTLIST) {
        shortlisted
    } else {
        rankedFallback()
    }.take(PRIMARY_HARD_CAP)

    val chosen = primary.mapTo(HashSet()) { it.modelId }
    return CuratedModels(
        primary = primary,
        // Ranked too, so "Show all" opens onto something ordered by usefulness rather than the
        // alphabetical-by-raw-id order the database hands us.
        remainder = filterNot { it.modelId in chosen }.sortedByDescending { it.usefulnessScore() },
    )
}

/**
 * What to lead with when there's no usable shortlist — a provider we don't hand-pick for, or one
 * whose list has gone stale. Strips everything that can't chat, prefers settled releases over
 * snapshots, and ranks the rest, so even an unknown OpenAI-compatible server produces a handful of
 * plausible models instead of its whole catalog.
 */
private fun List<ModelInfo>.rankedFallback(): List<ModelInfo> {
    val chatModels = filterNot { NON_CHAT_MODEL_KEYWORD.containsMatchIn(it.modelId) }
        .ifEmpty { this }
    // Only drop previews when enough settled models remain to fill the list — a catalog made
    // entirely of preview builds should still show something.
    val settled = chatModels.filterNot { PROVISIONAL_MODEL.containsMatchIn(it.modelId) }
    val candidates = if (settled.size >= PRIMARY_HARD_CAP) settled else chatModels
    return candidates.sortedWith(
        compareByDescending<ModelInfo> { it.usefulnessScore() }.thenByDescending { it.modelId },
    )
}

/**
 * A rough "would a newcomer want this one" score. Built on the same tier regexes the picker labels
 * models with, so what's ranked highest is what's described as most capable.
 */
private fun ModelInfo.usefulnessScore(): Int {
    var score = when (trait) {
        ModelTrait.MostCapable, ModelTrait.Reasoning -> 3
        ModelTrait.Fast -> 2
        null -> 1
    }
    if (supportsImages) score += 1
    if (contextWindowTokens >= 500_000) score += 2 else if (contextWindowTokens >= 100_000) score += 1
    if (PROVISIONAL_MODEL.containsMatchIn(modelId)) score -= 2
    // A newer generation of the same family is almost always the better default.
    score += LEADING_VERSION.find(modelId.substringAfterLast('/'))?.value?.toIntOrNull()?.coerceAtMost(9) ?: 0
    return score
}
