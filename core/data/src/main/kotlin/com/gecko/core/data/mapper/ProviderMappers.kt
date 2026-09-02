package com.gecko.core.data.mapper

import com.gecko.core.database.entity.ModelCatalogEntity
import com.gecko.core.database.entity.ProviderConfigEntity
import com.gecko.core.model.error.ErrorKind
import com.gecko.core.model.error.GeckoError
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
    // Rows written before v4 have no kind — Unknown still explains itself, which is more than the
    // raw provider string they were showing did.
    "FAILURE" -> ConnectionStatus.Failure(
        GeckoError(
            kind = ErrorKind.fromWireName(connectionErrorKind) ?: ErrorKind.Unknown,
            technicalDetail = connectionErrorMessage,
        ),
    )
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

internal fun ModelInfo.toEntity(configId: String): ModelCatalogEntity = ModelCatalogEntity(
    configId = configId,
    providerId = providerId.slug,
    modelId = modelId,
    displayName = displayName,
    contextWindowTokens = contextWindowTokens,
    supportsStreaming = supportsStreaming,
    supportsImages = supportsImages,
    fetchedAt = Instant.now().toEpochMilli(),
)
