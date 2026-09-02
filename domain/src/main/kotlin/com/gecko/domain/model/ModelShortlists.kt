package com.gecko.domain.model

import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderFlavor

/**
 * One slot in a provider's shortlist. [candidates] are tried in order and the first that resolves
 * fills the slot, so a tier can name its preferred model and then degrade through older ones for
 * accounts that don't have it yet.
 *
 * Each candidate is matched first as an exact model id, then as a prefix. The prefix rule is what
 * lets a shortlist survive a vendor's release cadence without an edit: Anthropic and OpenRouter
 * both publish dated ids (`claude-sonnet-4-5-20250929`), and a `claude-sonnet-4-5` candidate keeps
 * matching those as the dates roll forward.
 */
@JvmInline
value class ShortlistSlot(val candidates: List<String>)

private fun slot(vararg candidates: String) = ShortlistSlot(candidates.toList())

/**
 * The hand-picked models each provider leads with. Deliberately short — three to five per provider
 * is a choice a newcomer can actually make, where a hundred is a wall.
 *
 * These are the one part of curation that goes stale, since vendors rename and retire models on
 * their own schedule. That is survivable by design: a slot that resolves to nothing is skipped,
 * and if too few slots resolve the whole provider falls through to [rankedFallback], which never
 * shows more than the same hard cap. Staleness costs a worse shortlist, never a flood.
 */
internal val SHORTLISTS: Map<ProviderFlavor, List<ShortlistSlot>> = mapOf(
    ProviderFlavor.Google to listOf(
        slot("gemini-pro-latest", "gemini-3-pro", "gemini-2.5-pro"),
        // The moving `-latest` alias can land on a preview tier with a much tighter rate limit
        // than the dated stable releases, so dated ids lead and the alias is the fallback.
        slot("gemini-3.7-flash", "gemini-3.6-flash", "gemini-flash-latest", "gemini-2.5-flash"),
        slot("gemini-3.6-flash", "gemini-3.5-flash", "gemini-flash-latest", "gemini-2.5-flash", "gemini-2.5-flash-lite"),
    ),
    ProviderFlavor.OpenAi to listOf(
        slot("gpt-5.1", "gpt-5", "gpt-4.1", "gpt-4o"),
        slot("gpt-5.1-mini", "gpt-5-mini", "gpt-4.1-mini", "gpt-4o-mini"),
        slot("o4-mini", "o3-mini", "o3"),
    ),
    ProviderFlavor.Anthropic to listOf(
        slot("claude-opus-4-5", "claude-opus-4-1", "claude-opus-4"),
        slot("claude-sonnet-4-5", "claude-sonnet-4", "claude-3-7-sonnet"),
        slot("claude-haiku-4-5", "claude-3-5-haiku"),
    ),
    ProviderFlavor.OpenRouter to listOf(
        slot("anthropic/claude-sonnet-4.5", "anthropic/claude-sonnet-4"),
        slot("openai/gpt-5.1", "openai/gpt-5", "openai/gpt-4.1"),
        slot("google/gemini-3-pro", "google/gemini-2.5-pro"),
        slot("deepseek/deepseek-chat"),
        slot("meta-llama/llama-3.3-70b-instruct"),
    ),
    ProviderFlavor.NvidiaNim to listOf(
        slot("nvidia/nemotron-3.5-lightning-30b-a3b", "nvidia/llama-3.3-nemotron-super-49b"),
        slot("nvidia/nemotron-3-super-120b-a12b", "nvidia/llama-3.1-nemotron-70b-instruct"),
        slot("nvidia/nemotron-3-nano-30b-a3b", "meta/llama-3.3-70b-instruct"),
    ),
    ProviderFlavor.DeepSeek to listOf(
        slot("deepseek-chat"),
        slot("deepseek-reasoner"),
    ),
    ProviderFlavor.Kimi to listOf(
        slot("kimi-k2-turbo-preview", "kimi-latest"),
        slot("moonshot-v1-128k", "moonshot-v1-32k", "moonshot-v1-8k"),
    ),
    // CustomOpenAiCompatible is absent on purpose: there is nothing sensible to hand-pick for a
    // server we've never seen, so it gets the ranked fallback, which is exactly right for it.
)

/**
 * Fills each slot from [catalog], skipping any that nothing matches. A model already used by an
 * earlier slot can't fill a later one — overlapping fallback chains would otherwise show the same
 * model twice under two different tiers.
 */
internal fun List<ShortlistSlot>.resolveAgainst(catalog: List<ModelInfo>): List<ModelInfo> {
    val byId = catalog.associateBy { it.modelId }
    val taken = LinkedHashSet<String>()
    return mapNotNull { slot ->
        slot.candidates.firstNotNullOfOrNull { candidate ->
            val exact = byId[candidate]?.takeIf { it.modelId !in taken }
            // Prefix match sorted descending, so a dated family resolves to its newest release.
            val prefixed = catalog
                .filter { it.modelId.startsWith(candidate) && it.modelId !in taken }
                .maxByOrNull { it.modelId }
            exact ?: prefixed
        }?.also { taken += it.modelId }
    }
}
