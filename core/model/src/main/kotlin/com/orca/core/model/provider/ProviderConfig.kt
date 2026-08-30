package com.orca.core.model.provider

data class ProviderConfig(
    val providerId: ProviderId,
    val enabled: Boolean,
    val selectedModelId: String?,
    val baseUrlOverride: String?,
    val connectionStatus: ConnectionStatus,
    val hasApiKey: Boolean,
)
