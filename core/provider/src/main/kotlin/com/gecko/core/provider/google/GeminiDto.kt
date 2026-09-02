package com.gecko.core.provider.google

import kotlinx.serialization.Serializable

@Serializable
internal data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null,
)

/** Raw bytes for a non-text part — an AI-generated image, on image-output models. */
@Serializable
internal data class GeminiInlineData(
    val mimeType: String,
    val data: String,
)

@Serializable
internal data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart> = emptyList(),
)

@Serializable
internal data class GeminiSystemInstruction(
    val parts: List<GeminiPart>,
)

@Serializable
internal data class GeminiGenerationConfig(
    val responseModalities: List<String>? = null,
)

@Serializable
internal data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiSystemInstruction? = null,
    val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
internal data class GeminiUsageMetadata(
    val promptTokenCount: Int = 0,
    val candidatesTokenCount: Int = 0,
    val totalTokenCount: Int = 0,
)

@Serializable
internal data class GeminiCandidate(
    val content: GeminiContent = GeminiContent(),
    val finishReason: String? = null,
)

/**
 * [promptFeedback] is how Gemini reports that it refused the *prompt* — the response then carries
 * no candidates at all, which used to render as an empty bubble with nothing to explain it.
 * [error] appears when the API reports a failure inside an otherwise-normal 200 stream.
 */
@Serializable
internal data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val usageMetadata: GeminiUsageMetadata? = null,
    val promptFeedback: GeminiPromptFeedback? = null,
    val error: GeminiErrorDetail? = null,
)

@Serializable
internal data class GeminiPromptFeedback(
    val blockReason: String? = null,
)

@Serializable
internal data class GeminiModel(
    val name: String,
    val displayName: String = name,
    val inputTokenLimit: Int = 0,
    val supportedGenerationMethods: List<String> = emptyList(),
)

/**
 * [nextPageToken] matters more than it looks: Google's ListModels returns 50 per page by default,
 * and its catalog is comfortably longer than that. Ignoring the token silently truncated the list,
 * which in turn made any curated model id living past the first page look like it didn't exist.
 */
@Serializable
internal data class GeminiModelsResponse(
    val models: List<GeminiModel> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
internal data class GeminiErrorDetail(
    val message: String = "",
)

@Serializable
internal data class GeminiErrorResponse(
    val error: GeminiErrorDetail? = null,
)
