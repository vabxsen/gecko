package com.orca.core.model.provider

data class ModelInfo(
    val providerId: ProviderId,
    val modelId: String,
    val displayName: String,
    val contextWindowTokens: Int,
    val supportsStreaming: Boolean,
    val supportsImages: Boolean,
)
