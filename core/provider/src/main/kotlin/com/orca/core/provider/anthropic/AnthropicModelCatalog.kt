package com.orca.core.provider.anthropic

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
}
