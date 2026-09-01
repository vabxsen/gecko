package com.gecko.domain.model

import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelCurationTest {

    private fun model(id: String) = ModelInfo(
        providerId = ProviderId.GOOGLE,
        modelId = id,
        displayName = id,
        contextWindowTokens = 1_000_000,
        supportsStreaming = true,
        supportsImages = true,
    )

    private val junkModelIds = listOf(
        "antigravity-agent-preview",
        "deep-research-max-preview",
        "deep-research-preview",
        "deep-research-pro-preview",
        "gemini-2.5-computer-use-preview-10-2025",
        "gemini-2.5-flash-preview-tts",
        "gemini-2.5-pro-preview-tts",
        "gemini-3-flash-preview",
        "gemini-3.1-flash-lite-preview",
        "gemini-3.1-flash-tts-preview",
        "gemini-3.1-pro-preview",
        "gemini-3.1-pro-preview-custom-tools",
        "gemini-3.5-transcribe",
        "gemini-omni-1.1-flash",
        "gemini-omni-flash-preview",
        "gemini-robotics-er-1.6-preview",
        "gemini-robotics-er-2-preview",
        "gemma-4-26b-a4b-it",
        "gemma-4-31b-it",
        "lyria-3-clip-preview",
        "lyria-3-pro-preview",
        "nano-banana",
        "nano-banana-2",
        "nano-banana-2-lite",
        "nano-banana-pro",
    )

    private val geminiCatalogIds = listOf(
        "gemini-pro-latest",
        "gemini-flash-latest",
        "gemini-flash-lite-latest",
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.5-pro",
        "gemini-3.1-flash-lite",
        "gemini-3.5-flash",
        "gemini-3.5-flash-lite",
        "gemini-3.6-flash",
        "gemini-3.7-flash",
        "gemini-flash-latest",
        "gemini-flash-lite-latest",
        "gemini-pro-latest",
    )

    @Test
    fun GeminiShowsOnlyThreePreferredChatModelsWithFullCatalogAsAdvancedRemainder() {
        val models = (geminiCatalogIds + junkModelIds).map(::model)

        val curated = models.curatedForSelection(ProviderId.GOOGLE)

        assertEquals(
            listOf("gemini-pro-latest", "gemini-3.6-flash", "gemini-3.5-flash"),
            curated.primary.map { it.modelId },
        )
        assertEquals(true, curated.curatedAllowlistOnly)
        // Not surfaced in the compact chat picker, but still reachable (e.g. an advanced
        // Settings picker) rather than lost entirely.
        assertEquals(true, curated.hasMore)
        assertEquals(true, curated.remainder.containsAll(models.filter { it !in curated.primary }))
    }

    @Test
    fun GeminiFallsBackToRawCatalogWhenNoPreferredAliasesPresent() {
        val models = listOf("gemini-1.5-pro-001", "gemini-1.5-flash-001").map(::model)

        val curated = models.curatedForSelection(ProviderId.GOOGLE)

        assertEquals(models.map { it.modelId }, curated.primary.map { it.modelId })
        assertEquals(false, curated.curatedAllowlistOnly)
        assertEquals(false, curated.hasMore)
    }

    @Test
    fun GeminiFallsBackThroughStableVersionsWhenNeitherLatestNorDatedFlashIsAvailable() {
        // No -latest aliases and no 3.5/3.6 dated Flash ids — both Flash tiers must fall back,
        // and since they'd otherwise collide on the same "gemini-2.5-flash" id, the second tier
        // falls one step further to Flash-Lite so all three slots still resolve to distinct models.
        val models = listOf(
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash",
            "gemini-2.5-pro",
        ).map(::model)

        val curated = models.curatedForSelection(ProviderId.GOOGLE)

        assertEquals(
            listOf(
                "gemini-2.5-pro",
                "gemini-2.5-flash",
                "gemini-2.5-flash-lite",
            ),
            curated.primary.map { it.modelId },
        )
    }

    @Test
    fun GeminiFlashTiersNeverResolveToTheSameModelTwice() {
        // Only one Flash generation exists in this catalog — the second Flash tier's fallback
        // chain would otherwise re-resolve to the exact model the first tier already picked.
        val models = listOf("gemini-pro-latest", "gemini-flash-latest").map(::model)

        val curated = models.curatedForSelection(ProviderId.GOOGLE)

        assertEquals(listOf("gemini-pro-latest", "gemini-flash-latest"), curated.primary.map { it.modelId })
        assertEquals(curated.primary.size, curated.primary.map { it.modelId }.distinct().size)
    }

    @Test
    fun NvidiaNimShowsOnlySupportedNemotronChatShortlist() {
        val preferred = listOf(
            "nvidia/nemotron-3.5-lightning-30b-a3b",
            "nvidia/nemotron-3-super-120b-a12b",
            "nvidia/nemotron-3-nano-30b-a3b",
        )
        val noisyCatalog = preferred + listOf(
            "nvidia/nemotron-parse",
            "nvidia/cosmos-reason2-8b",
            "nvidia/nvclip",
            "meta/llama-3.2-11b-vision-instruct",
        )
        val models = noisyCatalog.map { ModelInfo(ProviderId.OPENAI, it, it, 128_000, true, false) }

        val curated = models.curatedForSelection(
            providerId = ProviderId.OPENAI,
            baseUrlOverride = "https://integrate.api.nvidia.com/v1",
        )

        assertEquals(preferred, curated.primary.map { it.modelId })
        assertEquals(true, curated.curatedAllowlistOnly)
        assertEquals(true, curated.hasMore)
        assertEquals(noisyCatalog.drop(3).toSet(), curated.remainder.map { it.modelId }.toSet())
    }

    @Test
    fun NvidiaNimFallsBackToRawCatalogWhenPreferredModelsUnavailable() {
        val noisyCatalog = listOf(
            "nvidia/nemotron-parse",
            "nvidia/cosmos-reason2-8b",
            "meta/llama-3.2-11b-vision-instruct",
        )
        val models = noisyCatalog.map { ModelInfo(ProviderId.OPENAI, it, it, 128_000, true, false) }

        val curated = models.curatedForSelection(
            providerId = ProviderId.OPENAI,
            baseUrlOverride = "https://integrate.api.nvidia.com/v1",
        )

        assertEquals(noisyCatalog, curated.primary.map { it.modelId })
        assertEquals(false, curated.curatedAllowlistOnly)
        assertEquals(false, curated.hasMore)
    }

    @Test
    fun doesNotCurateProvidersOutsideGoogleAndOpenAiProtocol() {
        val models = listOf("gpt-4o", "gpt-4o-mini", "o1-preview").map {
            ModelInfo(ProviderId.OPENROUTER, it, it, 128_000, true, true)
        }

        val curated = models.curatedForSelection(ProviderId.OPENROUTER)

        assertEquals(models, curated.primary)
        assertEquals(true, curated.remainder.isEmpty())
    }

    @Test
    fun keepsPlainChatModelIdsForOpenAiProtocolProviders() {
        val models = listOf("gpt-4o", "gpt-4o-mini", "o1-preview").map {
            ModelInfo(ProviderId.OPENAI, it, it, 128_000, true, true)
        }

        val curated = models.curatedForSelection(ProviderId.OPENAI)

        assertEquals(models, curated.primary)
        assertEquals(true, curated.remainder.isEmpty())
    }

    @Test
    fun filtersOutNonChatModelsForOpenAiProtocolProviders() {
        val chatIds = listOf(
            "gpt-4o",
            "gpt-3.5-turbo",
            "nvidia/llama-3.1-nemotron-70b-instruct",
            "deepseek-chat",
            "moonshot-v1-8k",
        )
        val nonChatIds = listOf(
            "text-embedding-3-small",
            "nvidia/nv-rerankqa-mistral-4b-v3",
            "whisper-1",
            "tts-1",
            "nvidia/parakeet-tts",
            "omni-moderation-latest",
            "meta/llama-guard-3-8b",
            "text-moderation-safety",
            "clip-vit-large",
            "text-ada-001",
            "davinci-instruct-beta",
            "dall-e-3",
            "gpt-4o-realtime-preview",
            "gpt-4o-mini-transcribe",
            "gpt-3.5-turbo-instruct",
            "computer-use-preview",
            "gpt-image-1",
            "davinci-002",
            "babbage-002",
        )
        val models = (chatIds + nonChatIds).map { ModelInfo(ProviderId.OPENAI, it, it, 128_000, true, true) }

        val curated = models.curatedForSelection(ProviderId.OPENAI)

        assertEquals(chatIds.toSet(), curated.primary.map { it.modelId }.toSet())
        assertEquals(nonChatIds.toSet(), curated.remainder.map { it.modelId }.toSet())
        assertEquals(true, curated.hasMore)
    }
}
