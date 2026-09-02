package com.gecko.core.model.provider

/**
 * Which service a saved key actually talks to, as opposed to which wire protocol it speaks.
 *
 * [ProviderId] can't answer this on its own: DeepSeek, Kimi, NVIDIA NIM and a user's own server
 * are all [ProviderId.OPENAI], separated only by [ProviderConfig.baseUrlOverride]. Every place
 * that needed to tell them apart — the brand logo, the curated model shortlist — was matching the
 * same host fragments independently, which is how a logo and a shortlist can quietly disagree
 * about what a key even is. This is the single answer both ask.
 */
enum class ProviderFlavor {
    OpenAi,
    Anthropic,
    Google,
    OpenRouter,
    DeepSeek,
    Kimi,
    NvidiaNim,

    /** An OpenAI-compatible endpoint we don't recognise — someone's self-hosted or niche server. */
    CustomOpenAiCompatible,
    ;

    companion object {
        fun of(providerId: ProviderId, baseUrlOverride: String?): ProviderFlavor = when (providerId) {
            ProviderId.ANTHROPIC -> Anthropic
            ProviderId.GOOGLE -> Google
            ProviderId.OPENROUTER -> OpenRouter
            ProviderId.OPENAI -> when {
                baseUrlOverride.isNullOrBlank() -> OpenAi
                baseUrlOverride.contains("deepseek", ignoreCase = true) -> DeepSeek
                baseUrlOverride.contains("moonshot", ignoreCase = true) -> Kimi
                baseUrlOverride.contains("nvidia", ignoreCase = true) -> NvidiaNim
                baseUrlOverride.contains("api.openai.com", ignoreCase = true) -> OpenAi
                else -> CustomOpenAiCompatible
            }
        }
    }
}
