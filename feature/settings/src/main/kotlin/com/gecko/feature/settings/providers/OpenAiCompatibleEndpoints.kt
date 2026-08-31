package com.gecko.feature.settings.providers

/**
 * Quick-pick endpoints for services that speak the same chat-completions dialect as OpenAI, so
 * they work through the existing OpenAI connector with just a different base URL. `baseUrl =
 * null` means "use OpenAI itself" (clears any override). The base URL field stays freely
 * editable in the UI too, so this list isn't a hard limit — any other OpenAI-compatible
 * endpoint (Groq, Together, a local server, etc.) can be typed in by hand.
 */
data class OpenAiCompatibleEndpoint(val label: String, val baseUrl: String?)

val OPENAI_COMPATIBLE_ENDPOINTS = listOf(
    OpenAiCompatibleEndpoint("OpenAI", null),
    OpenAiCompatibleEndpoint("DeepSeek", "https://api.deepseek.com/v1"),
    OpenAiCompatibleEndpoint("Kimi (Moonshot AI)", "https://api.moonshot.ai/v1"),
    OpenAiCompatibleEndpoint("NVIDIA NIM", "https://integrate.api.nvidia.com/v1"),
)
