package com.gecko.core.model.provider

data class ProviderConfig(
    val id: String,
    val providerId: ProviderId,
    val label: String,
    val enabled: Boolean,
    val baseUrlOverride: String?,
    val connectionStatus: ConnectionStatus,
    val hasApiKey: Boolean,
)
