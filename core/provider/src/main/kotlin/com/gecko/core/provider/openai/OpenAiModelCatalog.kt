package com.gecko.core.provider.openai

/**
 * OpenAI's `/models` endpoint doesn't return context window or capability metadata, so we
 * approximate it here for display purposes only. The model list itself always comes live
 * from the API - this only fills in cosmetic details.
 */
internal object OpenAiModelCatalog {
    private val knownPrefixes = listOf(
        "o3" to Meta(contextWindow = 200_000, supportsImages = true),
        "o1" to Meta(contextWindow = 200_000, supportsImages = true),
        "gpt-4o" to Meta(contextWindow = 128_000, supportsImages = true),
        "gpt-4.1" to Meta(contextWindow = 1_047_576, supportsImages = true),
        "gpt-4-turbo" to Meta(contextWindow = 128_000, supportsImages = true),
        "gpt-4" to Meta(contextWindow = 8_192, supportsImages = false),
        "gpt-3.5" to Meta(contextWindow = 16_385, supportsImages = false),
    )

    private val default = Meta(contextWindow = 128_000, supportsImages = false)

    fun metadataFor(modelId: String): Meta =
        knownPrefixes.firstOrNull { (prefix, _) -> modelId.startsWith(prefix) }?.second ?: default

    data class Meta(val contextWindow: Int, val supportsImages: Boolean)
}
