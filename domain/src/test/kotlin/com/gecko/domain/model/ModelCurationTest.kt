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

    private val stableModelIds = listOf(
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
    fun keepsOnlyCleanGeminiChatModelIds() {
        val models = (stableModelIds + junkModelIds).map(::model)

        val curated = models.curatedForSelection(ProviderId.GOOGLE)

        assertEquals(stableModelIds.toSet(), curated.primary.map { it.modelId }.toSet())
        assertEquals(junkModelIds.toSet(), curated.remainder.map { it.modelId }.toSet())
        assertEquals(true, curated.hasMore)
    }

    @Test
    fun sortsLatestAliasesFirstThenNewestVersionThenTier() {
        val models = listOf(
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-flash-lite-latest",
            "gemini-pro-latest",
            "gemini-flash-latest",
            "gemini-3.5-pro",
            "gemini-3.5-flash",
        ).map(::model)

        val curated = models.curatedForSelection(ProviderId.GOOGLE)

        assertEquals(
            listOf(
                "gemini-pro-latest",
                "gemini-flash-latest",
                "gemini-flash-lite-latest",
                "gemini-3.5-pro",
                "gemini-3.5-flash",
                "gemini-2.5-pro",
                "gemini-2.5-flash",
                "gemini-2.5-flash-lite",
            ),
            curated.primary.map { it.modelId },
        )
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
        val chatIds = listOf("gpt-4o", "nvidia/llama-3.1-nemotron-70b-instruct", "deepseek-chat")
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
        )
        val models = (chatIds + nonChatIds).map { ModelInfo(ProviderId.OPENAI, it, it, 128_000, true, true) }

        val curated = models.curatedForSelection(ProviderId.OPENAI)

        assertEquals(chatIds.toSet(), curated.primary.map { it.modelId }.toSet())
        assertEquals(nonChatIds.toSet(), curated.remainder.map { it.modelId }.toSet())
        assertEquals(true, curated.hasMore)
    }
}
