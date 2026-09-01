package com.gecko.core.provider.anthropic

/**
 * Anthropic's `/models` endpoint doesn't return context window or capability metadata, so we
 * approximate it here for display purposes only. The model list itself always comes live
 * from the API - this only fills in cosmetic details.
 */
internal object AnthropicModelCatalog {
    private const val DEFAULT_CONTEXT_WINDOW = 200_000
    private val textOnlyPrefixes = listOf("claude-1", "claude-2", "claude-instant")

    fun contextWindowFor(modelId: String): Int = DEFAULT_CONTEXT_WINDOW

    fun supportsImagesFor(modelId: String): Boolean =
        textOnlyPrefixes.none { modelId.startsWith(it) }

    /** Safe cross-model default: legacy models cap lower, every current Claude 3.x/4 model
     * supports at least this much output without needing beta headers. */
    fun maxOutputTokensFor(modelId: String): Int =
        if (textOnlyPrefixes.any { modelId.startsWith(it) }) 4096 else 8192
}
