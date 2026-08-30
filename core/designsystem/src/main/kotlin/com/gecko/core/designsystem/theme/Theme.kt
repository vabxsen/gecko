package com.gecko.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val GeckoDarkColorScheme = darkColorScheme(
    primary = GeckoDarkPrimary,
    onPrimary = GeckoDarkOnPrimary,
    primaryContainer = GeckoDarkPrimaryContainer,
    onPrimaryContainer = GeckoDarkOnPrimaryContainer,
    inversePrimary = GeckoDarkInversePrimary,
    secondary = GeckoDarkSecondary,
    onSecondary = GeckoDarkOnSecondary,
    secondaryContainer = GeckoDarkSecondaryContainer,
    onSecondaryContainer = GeckoDarkOnSecondaryContainer,
    tertiary = GeckoDarkTertiary,
    onTertiary = GeckoDarkOnTertiary,
    tertiaryContainer = GeckoDarkTertiaryContainer,
    onTertiaryContainer = GeckoDarkOnTertiaryContainer,
    background = GeckoDarkBackground,
    onBackground = GeckoDarkOnBackground,
    surface = GeckoDarkSurface,
    onSurface = GeckoDarkOnSurface,
    surfaceVariant = GeckoDarkSurfaceVariant,
    onSurfaceVariant = GeckoDarkOnSurfaceVariant,
    inverseSurface = GeckoDarkInverseSurface,
    inverseOnSurface = GeckoDarkInverseOnSurface,
    outline = GeckoDarkOutline,
    outlineVariant = GeckoDarkOutlineVariant,
    error = GeckoDarkError,
    onError = GeckoDarkOnError,
    errorContainer = GeckoDarkErrorContainer,
    onErrorContainer = GeckoDarkOnErrorContainer,
    scrim = GeckoDarkScrim,
    surfaceTint = GeckoDarkPrimary,
    surfaceContainer = GeckoDarkSurfaceContainer,
    surfaceContainerLow = GeckoDarkSurfaceContainerLow,
    surfaceContainerLowest = GeckoDarkSurfaceContainerLowest,
    surfaceContainerHigh = GeckoDarkSurfaceContainerHigh,
    surfaceContainerHighest = GeckoDarkSurfaceContainerHighest,
    surfaceBright = GeckoDarkSurfaceBright,
    surfaceDim = GeckoDarkSurfaceDim,
)

private val GeckoLightColorScheme = lightColorScheme(
    primary = GeckoLightPrimary,
    onPrimary = GeckoLightOnPrimary,
    primaryContainer = GeckoLightPrimaryContainer,
    onPrimaryContainer = GeckoLightOnPrimaryContainer,
    inversePrimary = GeckoLightInversePrimary,
    secondary = GeckoLightSecondary,
    onSecondary = GeckoLightOnSecondary,
    secondaryContainer = GeckoLightSecondaryContainer,
    onSecondaryContainer = GeckoLightOnSecondaryContainer,
    tertiary = GeckoLightTertiary,
    onTertiary = GeckoLightOnTertiary,
    tertiaryContainer = GeckoLightTertiaryContainer,
    onTertiaryContainer = GeckoLightOnTertiaryContainer,
    background = GeckoLightBackground,
    onBackground = GeckoLightOnBackground,
    surface = GeckoLightSurface,
    onSurface = GeckoLightOnSurface,
    surfaceVariant = GeckoLightSurfaceVariant,
    onSurfaceVariant = GeckoLightOnSurfaceVariant,
    inverseSurface = GeckoLightInverseSurface,
    inverseOnSurface = GeckoLightInverseOnSurface,
    outline = GeckoLightOutline,
    outlineVariant = GeckoLightOutlineVariant,
    error = GeckoLightError,
    onError = GeckoLightOnError,
    errorContainer = GeckoLightErrorContainer,
    onErrorContainer = GeckoLightOnErrorContainer,
    scrim = GeckoLightScrim,
    surfaceTint = GeckoLightPrimary,
    surfaceContainer = GeckoLightSurfaceContainer,
    surfaceContainerLow = GeckoLightSurfaceContainerLow,
    surfaceContainerLowest = GeckoLightSurfaceContainerLowest,
    surfaceContainerHigh = GeckoLightSurfaceContainerHigh,
    surfaceContainerHighest = GeckoLightSurfaceContainerHighest,
    surfaceBright = GeckoLightSurfaceBright,
    surfaceDim = GeckoLightSurfaceDim,
)

@Composable
fun GeckoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> GeckoDarkColorScheme
        else -> GeckoLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GeckoTypography,
        shapes = GeckoShapes,
        content = content,
    )
}
