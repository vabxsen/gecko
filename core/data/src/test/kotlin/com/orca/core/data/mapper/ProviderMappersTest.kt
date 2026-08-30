package com.orca.core.data.mapper

import com.orca.core.database.entity.ProviderConfigEntity
import com.orca.core.model.provider.ConnectionStatus
import com.orca.core.model.provider.ModelInfo
import com.orca.core.model.provider.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderMappersTest {

    @Test
    fun failureStatusRoundTripsWithMessage() {
        val entity = ProviderConfigEntity(
            providerId = ProviderId.OPENAI.slug,
            enabled = true,
            selectedModelId = "gpt-4o",
            baseUrlOverride = null,
            connectionStatus = ConnectionStatus.Failure("Invalid key").toWireString(),
            connectionErrorMessage = "Invalid key",
        )

        assertEquals(ConnectionStatus.Failure("Invalid key"), entity.toConnectionStatus())
    }

    @Test
    fun untestedIsDefaultForUnknownStatus() {
        val entity = ProviderConfigEntity(
            providerId = ProviderId.OPENAI.slug,
            enabled = false,
            selectedModelId = null,
            baseUrlOverride = null,
            connectionStatus = "SOMETHING_UNEXPECTED",
            connectionErrorMessage = null,
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

        val roundTripped = model.toEntity().toDomain()

        assertEquals(model, roundTripped)
    }
}
