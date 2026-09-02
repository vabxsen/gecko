package com.gecko.domain.model

import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTraitsTest {

    private fun model(
        id: String,
        contextWindowTokens: Int = 0,
        supportsImages: Boolean = false,
    ) = ModelInfo(
        providerId = ProviderId.GOOGLE,
        modelId = id,
        displayName = id,
        contextWindowTokens = contextWindowTokens,
        supportsStreaming = true,
        supportsImages = supportsImages,
    )

    @Test
    fun tagsTheQuickTierAsFast() {
        listOf(
            "gemini-3.6-flash",
            "gemini-2.5-flash-lite",
            "claude-haiku-4-5",
            "gpt-4.1-mini",
            "nvidia/nemotron-3-nano-30b-a3b",
            "nvidia/nemotron-3.5-lightning-30b-a3b",
            "llama-3.3-8b-instruct",
        ).forEach { id ->
            assertEquals("expected Fast for $id", ModelTrait.Fast, model(id).trait)
        }
    }

    @Test
    fun tagsTheTopTierAsMostCapable() {
        listOf(
            "gemini-pro-latest",
            "gemini-2.5-pro",
            "claude-opus-4-5",
            "nvidia/nemotron-3-super-120b-a12b",
            "llama-3.1-405b-instruct",
        ).forEach { id ->
            assertEquals("expected MostCapable for $id", ModelTrait.MostCapable, model(id).trait)
        }
    }

    @Test
    fun tagsReasoningModelsAheadOfTheirSizeTier() {
        listOf("deepseek-reasoner", "o3-mini", "qwq-32b", "gemini-2.5-flash-thinking").forEach { id ->
            assertEquals("expected Reasoning for $id", ModelTrait.Reasoning, model(id).trait)
        }
    }

    @Test
    fun aQuickTierIdIsNeverMislabelledAsTheHeavyweight() {
        // Both tiers' patterns match these; picking "Most capable" would send a newcomer to the
        // small model expecting the big one.
        assertEquals(ModelTrait.Fast, model("gemini-2.5-flash-lite").trait)
        assertEquals(ModelTrait.Fast, model("nvidia/nemotron-3.5-lightning-30b-a3b").trait)
    }

    @Test
    fun leavesUnremarkableMidTierIdsUntagged() {
        listOf("gpt-4o", "deepseek-chat", "kimi-k2", "mistral-medium").forEach { id ->
            assertNull("expected no trait for $id", model(id).trait)
        }
    }

    @Test
    fun aMidSizeParameterCountIsNotSoldAsTheHeavyweight() {
        // Sizes only earn "Most capable" from 60B up; a 12B model listed under that label beside a
        // 120B one is the confusion these tags exist to prevent.
        assertNull(model("mistralai/mistral-nemo-12b-instruct").trait)
        assertNull(model("nvidia/llama-3.1-nemotron-51b-instruct").trait)
        assertEquals(ModelTrait.MostCapable, model("meta/llama-3.1-70b-instruct").trait)
        assertEquals(ModelTrait.MostCapable, model("meta/llama-3.1-405b-instruct").trait)
    }

    @Test
    fun formatsContextWindowForHumans() {
        assertEquals("1M context", model("m", contextWindowTokens = 1_048_576).contextWindowLabel)
        assertEquals("128K context", model("m", contextWindowTokens = 128_000).contextWindowLabel)
        assertNull(model("m", contextWindowTokens = 0).contextWindowLabel)
    }

    @Test
    fun detailLineCombinesWhatIsKnownAndStaysEmptyWhenNothingIs() {
        assertEquals(
            "200K context · Reads images",
            model("m", contextWindowTokens = 200_000, supportsImages = true).detailLine,
        )
        assertEquals("200K context", model("m", contextWindowTokens = 200_000).detailLine)
        assertEquals("", model("m").detailLine)
    }

    @Test
    fun searchMatchesNameIdAndTrait() {
        val gemini = model("gemini-3.6-flash")
        assertTrue(gemini.matchesQuery("FLASH"))
        assertTrue(gemini.matchesQuery("gemini-3"))
        assertTrue("a trait label should be searchable", gemini.matchesQuery("fast"))
        assertTrue("a blank query matches everything", gemini.matchesQuery("   "))
        assertFalse(gemini.matchesQuery("claude"))
    }

    @Test
    fun aProviderAuthoredDisplayNameIsLeftAlone() {
        val google = model("gemini-3.6-flash").copy(displayName = "Gemini 3.6 Flash")
        assertEquals("Gemini 3.6 Flash", google.friendlyName)
    }

    @Test
    fun rawIdsBecomeReadableNames() {
        assertEquals(
            "Nemotron 3.5 Lightning 30B A3B",
            model("nvidia/nemotron-3.5-lightning-30b-a3b").friendlyName,
        )
        assertEquals("Nemotron 3 Super 120B A12B", model("nvidia/nemotron-3-super-120b-a12b").friendlyName)
        assertEquals("Kimi K2 Instruct", model("moonshotai/kimi-k2-instruct").friendlyName)
        assertEquals("Llama 3.3 70B Instruct", model("meta/llama-3.3-70b-instruct").friendlyName)
        assertEquals("Deepseek Reasoner", model("deepseek-reasoner").friendlyName)
    }

    @Test
    fun productNamesThatOnlyLookLikeSizesAreNotShouted() {
        // "4o" is part of the product name; "8b" is a parameter count. Only the latter is a size.
        assertEquals("GPT 4o", model("gpt-4o").friendlyName)
        assertEquals("Llama 3 8B", model("llama-3-8b").friendlyName)
    }

    @Test
    fun theRawIdStaysSearchableAfterPrettifying() {
        val nemotron = model("nvidia/nemotron-3-super-120b-a12b")
        assertEquals("Nemotron 3 Super 120B A12B", nemotron.friendlyName)
        assertTrue("the id a user copied from NVIDIA still finds it", nemotron.matchesQuery("nvidia/nemotron"))
        assertTrue(nemotron.matchesQuery("120b"))
    }

    @Test
    fun anEmptyDisplayNameFallsBackRatherThanRenderingBlank() {
        assertEquals("Gpt 4", model("gpt 4").copy(displayName = "").friendlyName)
        assertEquals("---", model("---").copy(displayName = "").friendlyName)
    }
}
