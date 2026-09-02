package com.gecko.domain.model

import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderFlavor
import com.gecko.core.model.provider.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCurationTest {

    private fun model(
        id: String,
        providerId: ProviderId = ProviderId.GOOGLE,
        contextWindowTokens: Int = 1_000_000,
        supportsImages: Boolean = true,
    ) = ModelInfo(
        providerId = providerId,
        modelId = id,
        displayName = id,
        contextWindowTokens = contextWindowTokens,
        supportsStreaming = true,
        supportsImages = supportsImages,
    )

    /** A real sample of what Google's `/models` returns alongside its chat models. */
    private val junkModelIds = listOf(
        "antigravity-agent-preview",
        "deep-research-max-preview",
        "gemini-2.5-computer-use-preview-10-2025",
        "gemini-2.5-flash-preview-tts",
        "gemini-3.1-flash-tts-preview",
        "gemini-3.5-transcribe",
        "gemini-robotics-er-2-preview",
        "lyria-3-pro-preview",
        "nano-banana-pro",
        "text-embedding-004",
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
    )

    private val nvidiaBaseUrl = "https://integrate.api.nvidia.com/v1"

    // --- The shortlist, when it resolves ---------------------------------------------------

    @Test
    fun geminiLeadsWithAShortlistAndKeepsTheRestBehindShowAll() {
        val models = (geminiCatalogIds + junkModelIds).map(::model)

        val curated = models.curatedForSelection(ProviderId.GOOGLE)

        assertEquals(
            listOf("gemini-pro-latest", "gemini-3.7-flash", "gemini-3.6-flash"),
            curated.primary.map { it.modelId },
        )
        assertTrue(curated.hasMore)
        assertTrue(curated.remainder.none { it.modelId in curated.primary.map { p -> p.modelId } })
    }

    @Test
    fun aTierFallsThroughToOlderReleasesRatherThanLeavingAnEmptySlot() {
        val models = listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite").map(::model)

        val curated = models.curatedForSelection(ProviderId.GOOGLE)

        assertEquals(
            listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite"),
            curated.primary.map { it.modelId },
        )
    }

    @Test
    fun twoTiersNeverResolveToTheSameModel() {
        // Both flash tiers fall back to the same alias when their preferred ids are missing.
        val models = listOf("gemini-pro-latest", "gemini-flash-latest").map(::model)

        val curated = models.curatedForSelection(ProviderId.GOOGLE)

        assertEquals(curated.primary.size, curated.primary.map { it.modelId }.toSet().size)
    }

    @Test
    fun nvidiaNimIsRecognisedByItsBaseUrlNotItsProviderId() {
        val models = listOf(
            "nvidia/nemotron-3.5-lightning-30b-a3b",
            "nvidia/nemotron-3-super-120b-a12b",
            "nvidia/nemotron-3-nano-30b-a3b",
            "meta/llama-3.1-8b-instruct",
            "baai/bge-m3",
        ).map { model(it, providerId = ProviderId.OPENAI) }

        val curated = models.curatedForSelection(ProviderId.OPENAI, nvidiaBaseUrl)

        assertEquals(
            listOf(
                "nvidia/nemotron-3.5-lightning-30b-a3b",
                "nvidia/nemotron-3-super-120b-a12b",
                "nvidia/nemotron-3-nano-30b-a3b",
            ),
            curated.primary.map { it.modelId },
        )
    }

    @Test
    fun datedVendorIdsResolveByPrefixToTheNewestRelease() {
        // Anthropic and OpenRouter both ship dated ids. Without prefix matching the shortlist
        // would need editing every time a vendor cuts a release.
        val models = listOf(
            "claude-opus-4-5-20251101",
            "claude-sonnet-4-5-20250929",
            "claude-sonnet-4-5-20250815",
            "claude-haiku-4-5-20251001",
        ).map { model(it, providerId = ProviderId.ANTHROPIC) }

        val curated = models.curatedForSelection(ProviderId.ANTHROPIC)

        assertEquals(
            listOf("claude-opus-4-5-20251101", "claude-sonnet-4-5-20250929", "claude-haiku-4-5-20251001"),
            curated.primary.map { it.modelId },
        )
    }

    // --- The providers that used to get no curation at all ----------------------------------

    @Test
    fun openRouterNoLongerRendersItsEntireCatalog() {
        // The old `else` branch put all of these in `primary` with an empty `remainder`, so the
        // picker couldn't even draw a "Show all" toggle to collapse them.
        val catalog = (1..400).map { model("vendor-$it/model-$it", providerId = ProviderId.OPENROUTER) } +
            listOf("anthropic/claude-sonnet-4.5", "openai/gpt-5.1")
                .map { model(it, providerId = ProviderId.OPENROUTER) }

        val curated = catalog.curatedForSelection(ProviderId.OPENROUTER)

        assertTrue("primary was ${curated.primary.size}", curated.primary.size <= PRIMARY_CAP)
        assertTrue(curated.remainder.size >= 390)
        assertTrue(curated.primary.map { it.modelId }.containsAll(listOf("anthropic/claude-sonnet-4.5", "openai/gpt-5.1")))
    }

    @Test
    fun openAiNoLongerLeadsWithWhateverSortsFirstAlphabetically() {
        // `babbage-002` sorting above `gpt-5` is how the auto-selector used to hand a first-time
        // user a completion model that can't chat.
        val models = listOf(
            "babbage-002",
            "davinci-002",
            "text-embedding-3-large",
            "gpt-5.1",
            "gpt-5.1-mini",
            "gpt-4o",
            "o4-mini",
        ).map { model(it, providerId = ProviderId.OPENAI) }

        val curated = models.curatedForSelection(ProviderId.OPENAI)

        assertEquals("gpt-5.1", curated.primary.first().modelId)
        assertTrue(curated.primary.none { it.modelId == "babbage-002" })
        assertTrue(curated.primary.size <= PRIMARY_CAP)
    }

    // --- Degradation: the path that used to produce the flood --------------------------------

    @Test
    fun aStaleShortlistFallsBackToRankedChatModelsNotTheWholeCatalog() {
        // Every shortlist id renamed away. This used to return the entire raw catalog, junk and
        // all, which is precisely how three curated models became fifty.
        val renamed = (1..30).map { "gemini-9.$it-quantum" }
        val models = (renamed + junkModelIds).map(::model)

        val curated = models.curatedForSelection(ProviderId.GOOGLE)

        assertTrue("primary was ${curated.primary.size}", curated.primary.size <= PRIMARY_CAP)
        assertTrue(curated.primary.none { it.modelId in junkModelIds })
        assertTrue(curated.hasMore)
    }

    @Test
    fun theFallbackNeverStrandsTheUserWithNothingToPick() {
        // A catalog of nothing but previews: dropping them all would leave an unusable app.
        val models = listOf("some-model-preview", "other-model-exp").map(::model)

        val curated = models.curatedForSelection(ProviderId.OPENROUTER)

        assertTrue(curated.primary.isNotEmpty())
    }

    @Test
    fun anUnrecognisedOpenAiCompatibleServerStillGetsAShortList() {
        val models = (1..50).map { model("local-model-$it", providerId = ProviderId.OPENAI) }

        val curated = models.curatedForSelection(ProviderId.OPENAI, "https://my-own-server.test/v1")

        assertTrue(curated.primary.size <= PRIMARY_CAP)
        assertEquals(50, curated.primary.size + curated.remainder.size)
    }

    @Test
    fun nonChatModelsAreNeverOfferedAsTheLeadChoice() {
        val models = listOf(
            "text-embedding-3-large",
            "whisper-1",
            "tts-1-hd",
            "omni-moderation-latest",
            "some-chat-model",
        ).map { model(it, providerId = ProviderId.OPENAI) }

        val curated = models.curatedForSelection(ProviderId.OPENAI, "https://my-own-server.test/v1")

        assertEquals(listOf("some-chat-model"), curated.primary.map { it.modelId })
    }

    // --- Invariants that must hold for every provider ----------------------------------------

    @Test
    fun noProviderEverLeadsWithMoreThanTheCap() {
        val catalog = (1..200).map { model("model-$it", providerId = ProviderId.OPENAI) }
        val urlFor = mapOf(
            ProviderFlavor.DeepSeek to "https://api.deepseek.com/v1",
            ProviderFlavor.Kimi to "https://api.moonshot.ai/v1",
            ProviderFlavor.NvidiaNim to nvidiaBaseUrl,
            ProviderFlavor.CustomOpenAiCompatible to "https://elsewhere.test/v1",
        )

        ProviderId.entries.forEach { providerId ->
            val curated = catalog.curatedForSelection(providerId)
            assertTrue("$providerId led with ${curated.primary.size}", curated.primary.size <= PRIMARY_CAP)
        }
        urlFor.forEach { (flavor, url) ->
            val curated = catalog.curatedForSelection(ProviderId.OPENAI, url)
            assertTrue("$flavor led with ${curated.primary.size}", curated.primary.size <= PRIMARY_CAP)
        }
    }

    @Test
    fun everyModelIsEitherLedWithOrReachableBehindShowAll() {
        val models = (geminiCatalogIds + junkModelIds).map(::model)

        val curated = models.curatedForSelection(ProviderId.GOOGLE)

        assertEquals(
            models.map { it.modelId }.toSet(),
            (curated.primary + curated.remainder).map { it.modelId }.toSet(),
        )
    }

    @Test
    fun theModelAdoptedForANewUserIsTheEverydayTierNotTheFlagship() {
        // Measured against a real free Gemini key: the top tier answered every request with a 429
        // while the quick tier worked. Auto-adopting a model that can't reply is the exact failure
        // this is meant to prevent.
        val models = (geminiCatalogIds + junkModelIds).map(::model)

        val curated = models.curatedForSelection(ProviderId.GOOGLE)

        assertEquals("gemini-pro-latest", curated.primary.first().modelId)
        assertEquals("gemini-3.7-flash", curated.defaultChoice?.modelId)
    }

    @Test
    fun aProviderWithNoQuickTierStillGetsADefault() {
        val models = listOf("claude-opus-4-5", "claude-sonnet-4-5").map { model(it, ProviderId.ANTHROPIC) }

        val curated = models.curatedForSelection(ProviderId.ANTHROPIC)

        assertEquals(curated.primary.first().modelId, curated.defaultChoice?.modelId)
    }

    @Test
    fun anEmptyCatalogCuratesToNothingRatherThanThrowing() {
        val curated = emptyList<ModelInfo>().curatedForSelection(ProviderId.OPENAI)

        assertTrue(curated.primary.isEmpty())
        assertTrue(curated.remainder.isEmpty())
    }

    private companion object {
        /** Mirrors `PRIMARY_HARD_CAP`; duplicated because the constant is private to the algorithm. */
        const val PRIMARY_CAP = 6
    }
}
