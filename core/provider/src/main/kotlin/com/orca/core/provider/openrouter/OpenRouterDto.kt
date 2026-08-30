package com.orca.core.provider.openrouter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OpenRouterArchitecture(
    val modality: String = "",
)

@Serializable
internal data class OpenRouterModel(
    val id: String,
    val name: String = id,
    @SerialName("context_length") val contextLength: Int = 0,
    val architecture: OpenRouterArchitecture = OpenRouterArchitecture(),
)

@Serializable
internal data class OpenRouterModelsResponse(
    val data: List<OpenRouterModel> = emptyList(),
)
