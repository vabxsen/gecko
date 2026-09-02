package com.gecko.core.provider.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
internal data class OpenAiMessage(
    val role: String,
    val content: JsonElement,
)

/**
 * [content] is nullable because a reply is not guaranteed to have any: reasoning models can put
 * everything in [reasoningContent], and a refusal or a tool-only turn returns an explicit
 * `"content": null`. Decoding that into a non-null `String` threw a raw kotlinx
 * `SerializationException` at the user instead of an error they could act on.
 */
@Serializable
internal data class OpenAiResponseMessage(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
)

/**
 * [includeUsage] deliberately has no default value.
 *
 * kotlinx.serialization omits any property that equals its default, so `includeUsage = true` with
 * a default of `true` serialized as an empty `"stream_options":{}`. OpenAI ignores that; NVIDIA NIM
 * rejects the whole request with `400 missing field 'include_usage'`, which meant *every* streaming
 * message to NVIDIA failed. Without a default the field is always written.
 */
@Serializable
internal data class OpenAiStreamOptions(
    @SerialName("include_usage") val includeUsage: Boolean,
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

/**
 * [reasoningContent] carries a reasoning model's working-out. DeepSeek's reasoner and NVIDIA's
 * Nemotron thinking tiers stream into it, and some of them put the *whole* answer there and never
 * populate [content] at all — parsing only [content] rendered those replies as an empty bubble.
 */
@Serializable
internal data class OpenAiDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
)

@Serializable
internal data class OpenAiStreamChoice(
    val delta: OpenAiDelta = OpenAiDelta(),
    @SerialName("finish_reason") val finishReason: String? = null,
)

/**
 * [error] is not part of OpenAI's own streaming contract, but OpenRouter and NVIDIA NIM both
 * report mid-stream failures (insufficient credits, an upstream model going away) by sending an
 * error object as a `data:` frame and then closing the stream normally. Decoding it here is what
 * stops that turning into a silent empty reply.
 */
@Serializable
internal data class OpenAiStreamChunk(
    val choices: List<OpenAiStreamChoice> = emptyList(),
    val usage: OpenAiUsage? = null,
    val error: OpenAiErrorDetail? = null,
)

@Serializable
internal data class OpenAiChoice(
    val message: OpenAiResponseMessage = OpenAiResponseMessage(),
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class OpenAiChatResponse(
    val choices: List<OpenAiChoice> = emptyList(),
    val usage: OpenAiUsage? = null,
    val error: OpenAiErrorDetail? = null,
)

@Serializable
internal data class OpenAiModel(
    val id: String,
)

@Serializable
internal data class OpenAiModelsResponse(
    val data: List<OpenAiModel> = emptyList(),
)

/**
 * [code] is deliberately a raw [JsonElement]: OpenAI sends a string slug ("invalid_api_key"),
 * OpenRouter sends a bare HTTP status number, and a strict type for either one makes the other
 * fail to decode. [httpStatusCode] reads it back only when it really is a status number.
 */
@Serializable
internal data class OpenAiErrorDetail(
    val message: String = "",
    val code: JsonElement? = null,
) {
    val httpStatusCode: Int?
        get() = (code as? JsonPrimitive)?.contentOrNull?.toIntOrNull()?.takeIf { it in 100..599 }
}

@Serializable
internal data class OpenAiErrorResponse(
    val error: OpenAiErrorDetail? = null,
)
