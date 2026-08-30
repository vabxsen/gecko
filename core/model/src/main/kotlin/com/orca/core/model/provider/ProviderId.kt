package com.orca.core.model.provider

enum class ProviderId(val slug: String, val displayName: String) {
    OPENAI("openai", "OpenAI"),
    ANTHROPIC("anthropic", "Anthropic"),
    GOOGLE("google", "Google Gemini"),
    OPENROUTER("openrouter", "OpenRouter");

    companion object {
        fun fromSlug(slug: String): ProviderId? = entries.find { it.slug == slug }
    }
}
