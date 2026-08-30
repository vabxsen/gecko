package com.gecko.core.data.mapper

import com.gecko.core.database.entity.ProviderConfigEntity
import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderMappersTest {

    @Test
    fun failureStatusRoundTripsWithMessage() {
        val entity = ProviderConfigEntity(
            id = "config-1",
            providerId = ProviderId.OPENAI.slug,
            label = "OpenAI",
            enabled = true,
            selectedModelId = "gpt-4o",
            baseUrlOverride = null,
            connectionStatus = ConnectionStatus.Failure("Invalid key").toWireString(),
            connectionErrorMessage = "Invalid key",
            createdAt = 0L,
        )

        assertEquals(ConnectionStatus.Failure("Invalid key"), entity.toConnectionStatus())
    }

    @Test
    fun untestedIsDefaultForUnknownStatus() {
        val entity = ProviderConfigEntity(
            id = "config-1",
            providerId = ProviderId.OPENAI.slug,
            label = "OpenAI",
            enabled = false,
            selectedModelId = null,
            baseUrlOverride = null,
            connectionStatus = "SOMETHING_UNEXPECTED",
            connectionErrorMessage = null,
            createdAt = 0L,
        )

        assertEquals(ConnectionStatus.Untested, entity.toConnectionStatus())
    }

    @Test
    fun modelInfoRoundTripsThroughEntity() {
        val model = ModelInfo(
            providerId = ProviderId.GOOGLE,
            modelId = "gemini-1.5-pro",
            displayName = "Gemini 1.5 Pro",
            contextWindowTokens = 2_000_000,
            supportsStreaming = true,
            supportsImages = true,
        )

        val roundTripped = model.toEntity("config-1").toDomain()

        assertEquals(model, roundTripped)
    }
}
