package com.gecko.core.designsystem.icon

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gecko.core.designsystem.R
import com.gecko.core.model.provider.ProviderId

/**
 * The real brand mark for a saved provider config, so "Add API key"/"AI Providers"/the chat
 * model picker show NVIDIA's logo for NVIDIA NIM, Gemini's for Google, etc. instead of one
 * generic icon for everything.
 *
 * [ProviderId.OPENAI] alone covers four different brands (OpenAI itself, DeepSeek, Kimi, NVIDIA
 * NIM) that all speak the OpenAI-compatible protocol through [baseUrlOverride] — so the OpenAI
 * branch below disambiguates by matching known host fragments from
 * `feature/settings/providers/OpenAiCompatibleEndpoints.kt`'s preset base URLs. An unrecognized
 * custom base URL (a user's own OpenAI-compatible server) falls back to the plain OpenAI mark,
 * since that's the protocol actually being spoken.
 */
@DrawableRes
fun providerLogoRes(providerId: ProviderId, baseUrlOverride: String?): Int = when (providerId) {
    ProviderId.ANTHROPIC -> R.drawable.ic_provider_anthropic
    ProviderId.GOOGLE -> R.drawable.ic_provider_google
    ProviderId.OPENROUTER -> R.drawable.ic_provider_openrouter
    ProviderId.OPENAI -> when {
        baseUrlOverride?.contains("deepseek", ignoreCase = true) == true -> R.drawable.ic_provider_deepseek
        baseUrlOverride?.contains("moonshot", ignoreCase = true) == true -> R.drawable.ic_provider_kimi
        baseUrlOverride?.contains("nvidia", ignoreCase = true) == true -> R.drawable.ic_provider_nvidia
        else -> R.drawable.ic_provider_openai
    }
}

/** A provider's real brand mark on a neutral circular chip, sized consistently regardless of
 * each logo's own natural proportions (NVIDIA's fills its bounds, Gemini's four-pointed star
 * doesn't, etc). */
@Composable
fun ProviderLogo(providerId: ProviderId, baseUrlOverride: String?, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = providerLogoRes(providerId, baseUrlOverride)),
            contentDescription = null,
            modifier = Modifier.size(size * 0.58f),
        )
    }
}
