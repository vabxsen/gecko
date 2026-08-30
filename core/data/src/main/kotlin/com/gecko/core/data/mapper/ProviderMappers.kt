package com.gecko.core.data.mapper

import com.gecko.core.database.entity.ModelCatalogEntity
import com.gecko.core.database.entity.ProviderConfigEntity
import com.gecko.core.model.provider.ConnectionStatus
import com.gecko.core.model.provider.ModelInfo
import com.gecko.core.model.provider.ProviderId
import java.time.Instant

internal fun ConnectionStatus.toWireString(): String = when (this) {
    ConnectionStatus.Untested -> "UNTESTED"
    ConnectionStatus.Testing -> "TESTING"
    ConnectionStatus.Success -> "SUCCESS"
    is ConnectionStatus.Failure -> "FAILURE"
}

internal fun ProviderConfigEntity.toConnectionStatus(): ConnectionStatus = when (connectionStatus) {
    "TESTING" -> ConnectionStatus.Testing
    "SUCCESS" -> ConnectionStatus.Success
    "FAILURE" -> ConnectionStatus.Failure(connectionErrorMessage ?: "Connection failed")
    else -> ConnectionStatus.Untested
}

internal fun ModelCatalogEntity.toDomain(): ModelInfo = ModelInfo(
    providerId = ProviderId.fromSlug(providerId) ?: error("Unknown provider slug: $providerId"),
    modelId = modelId,
    displayName = displayName,
    contextWindowTokens = contextWindowTokens,
    supportsStreaming = supportsStreaming,
    supportsImages = supportsImages,
)

internal fun ModelInfo.toEntity(): ModelCatalogEntity = ModelCatalogEntity(
    providerId = providerId.slug,
    modelId = modelId,
    displayName = displayName,
    contextWindowTokens = contextWindowTokens,
    supportsStreaming = supportsStreaming,
    supportsImages = supportsImages,
    fetchedAt = Instant.now().toEpochMilli(),
)
