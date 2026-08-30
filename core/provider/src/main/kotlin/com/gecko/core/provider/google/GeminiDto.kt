package com.gecko.core.provider.google

import kotlinx.serialization.Serializable

@Serializable
internal data class GeminiPart(
    val text: String,
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
internal data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiSystemInstruction? = null,
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

@Serializable
internal data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val usageMetadata: GeminiUsageMetadata? = null,
)

@Serializable
internal data class GeminiModel(
    val name: String,
    val displayName: String = name,
    val inputTokenLimit: Int = 0,
    val supportedGenerationMethods: List<String> = emptyList(),
)

@Serializable
internal data class GeminiModelsResponse(
    val models: List<GeminiModel> = emptyList(),
)

@Serializable
internal data class GeminiErrorDetail(
    val message: String = "",
)

@Serializable
internal data class GeminiErrorResponse(
    val error: GeminiErrorDetail? = null,
)
