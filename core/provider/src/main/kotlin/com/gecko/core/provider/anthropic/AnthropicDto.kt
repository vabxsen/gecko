package com.gecko.core.provider.anthropic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AnthropicMessage(
    val role: String,
    val content: List<AnthropicContentBlock>,
)

@Serializable
internal data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String? = null,
    val messages: List<AnthropicMessage>,
    val stream: Boolean,
)

@Serializable
internal data class AnthropicUsage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
)

@Serializable
internal data class AnthropicContentBlock(
    val type: String = "text",
    val text: String = "",
    val source: AnthropicImageSource? = null,
)

@Serializable
internal data class AnthropicImageSource(
    val type: String = "base64",
    @SerialName("media_type") val mediaType: String,
    val data: String,
)

@Serializable
internal data class AnthropicResponse(
    val content: List<AnthropicContentBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: AnthropicUsage? = null,
)

@Serializable
internal data class AnthropicEventDelta(
    val type: String? = null,
    val text: String? = null,
    @SerialName("stop_reason") val stopReason: String? = null,
)

@Serializable
internal data class AnthropicEventMessage(
    val usage: AnthropicUsage? = null,
)

@Serializable
internal data class AnthropicErrorDetail(
    val type: String? = null,
    val message: String = "",
)

@Serializable
internal data class AnthropicStreamEvent(
    val type: String = "",
    val delta: AnthropicEventDelta? = null,
    val usage: AnthropicUsage? = null,
    val message: AnthropicEventMessage? = null,
    val error: AnthropicErrorDetail? = null,
)

@Serializable
internal data class AnthropicModel(
    val id: String,
    @SerialName("display_name") val displayName: String = id,
)

@Serializable
internal data class AnthropicModelsResponse(
    val data: List<AnthropicModel> = emptyList(),
)

@Serializable
internal data class AnthropicErrorResponse(
    val error: AnthropicErrorDetail? = null,
)
