package com.gecko.core.provider.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OpenAiMessage(
    val role: String,
    val content: String,
)

@Serializable
internal data class OpenAiStreamOptions(
    @SerialName("include_usage") val includeUsage: Boolean = true,
)

@Serializable
internal data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean,
    @SerialName("stream_options") val streamOptions: OpenAiStreamOptions? = null,
)

@Serializable
internal data class OpenAiUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

@Serializable
internal data class OpenAiDelta(
    val role: String? = null,
    val content: String? = null,
)

@Serializable
internal data class OpenAiStreamChoice(
    val delta: OpenAiDelta = OpenAiDelta(),
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class OpenAiStreamChunk(
    val choices: List<OpenAiStreamChoice> = emptyList(),
    val usage: OpenAiUsage? = null,
)

@Serializable
internal data class OpenAiChoice(
    val message: OpenAiMessage,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class OpenAiChatResponse(
    val choices: List<OpenAiChoice> = emptyList(),
    val usage: OpenAiUsage? = null,
)

@Serializable
internal data class OpenAiModel(
    val id: String,
)

@Serializable
internal data class OpenAiModelsResponse(
    val data: List<OpenAiModel> = emptyList(),
)

@Serializable
internal data class OpenAiErrorDetail(
    val message: String = "",
)

@Serializable
internal data class OpenAiErrorResponse(
    val error: OpenAiErrorDetail? = null,
)
