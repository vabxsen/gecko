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
import com.gecko.core.model.provider.ProviderFlavor
import com.gecko.core.model.provider.ProviderId

/**
 * The real brand mark for a saved provider config, so "Add API key"/"AI Providers"/the chat
 * model picker show NVIDIA's logo for NVIDIA NIM, Gemini's for Google, etc. instead of one
 * generic icon for everything.
 *
 * [ProviderId.OPENAI] alone covers four different brands (OpenAI itself, DeepSeek, Kimi, NVIDIA
 * NIM) that all speak the OpenAI-compatible protocol through [baseUrlOverride], so the brand is
 * resolved through [ProviderFlavor] — the same answer the curated model shortlist uses, so a key's
 * logo and its shortlist can never disagree about what service it is. An unrecognized custom base
 * URL (a user's own OpenAI-compatible server) falls back to the plain OpenAI mark, since that's
 * the protocol actually being spoken.
 */
@DrawableRes
fun providerLogoRes(providerId: ProviderId, baseUrlOverride: String?): Int =
    when (ProviderFlavor.of(providerId, baseUrlOverride)) {
        ProviderFlavor.Anthropic -> R.drawable.ic_provider_anthropic
        ProviderFlavor.Google -> R.drawable.ic_provider_google
        ProviderFlavor.OpenRouter -> R.drawable.ic_provider_openrouter
        ProviderFlavor.DeepSeek -> R.drawable.ic_provider_deepseek
        ProviderFlavor.Kimi -> R.drawable.ic_provider_kimi
        ProviderFlavor.NvidiaNim -> R.drawable.ic_provider_nvidia
        ProviderFlavor.OpenAi, ProviderFlavor.CustomOpenAiCompatible -> R.drawable.ic_provider_openai
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
