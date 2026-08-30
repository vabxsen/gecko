package com.gecko.core.provider.api

import com.gecko.core.model.provider.ProviderId
import com.gecko.core.provider.anthropic.AnthropicProvider
import com.gecko.core.provider.google.GoogleGeminiProvider
import com.gecko.core.provider.openai.OpenAiProvider
import com.gecko.core.provider.openrouter.OpenRouterProvider
import okhttp3.OkHttpClient

/**
 * Builds a fresh [AiProvider] instance for a given provider + API key. Providers are cheap
 * to construct, so callers should create a new one whenever the key/config might have
 * changed rather than caching instances long-term.
 */
class ProviderFactory(private val httpClient: OkHttpClient) {

    fun create(providerId: ProviderId, apiKey: String, baseUrlOverride: String? = null): AiProvider = when (providerId) {
        ProviderId.OPENAI -> OpenAiProvider(
            apiKey = apiKey,
            baseUrl = baseUrlOverride ?: OpenAiProvider.DEFAULT_BASE_URL,
            httpClient = httpClient,
        )
        ProviderId.ANTHROPIC -> AnthropicProvider(
            apiKey = apiKey,
            baseUrl = baseUrlOverride ?: AnthropicProvider.DEFAULT_BASE_URL,
            httpClient = httpClient,
        )
        ProviderId.GOOGLE -> GoogleGeminiProvider(
            apiKey = apiKey,
            baseUrl = baseUrlOverride ?: GoogleGeminiProvider.DEFAULT_BASE_URL,
            httpClient = httpClient,
        )
        ProviderId.OPENROUTER -> OpenRouterProvider(
            apiKey = apiKey,
            baseUrl = baseUrlOverride ?: OpenRouterProvider.DEFAULT_BASE_URL,
            httpClient = httpClient,
        )
    }
}
