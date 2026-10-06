package com.gecko.core.provider.openrouter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OpenRouterArchitecture(
    val modality: String = "",
    @SerialName("input_modalities") val inputModalities: List<String>? = null,
    @SerialName("output_modalities") val outputModalities: List<String>? = null,
) {
    // Older catalogs only supply the combined string. Image output does not imply image input.
    val inputs: List<String>
        get() = inputModalities ?: modality.substringBefore("->").ifBlank { "text" }.split('+')
    val outputs: List<String>
        get() = outputModalities ?: modality.substringAfter("->", "text").split('+')
}

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
