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
    secondary = GeckoDarkSecondary,
    onSecondary = GeckoDarkOnSecondary,
    background = GeckoDarkBackground,
    onBackground = GeckoDarkOnBackground,
    surface = GeckoDarkSurface,
    onSurface = GeckoDarkOnSurface,
    surfaceVariant = GeckoDarkSurfaceVariant,
    onSurfaceVariant = GeckoDarkOnSurfaceVariant,
    surfaceContainer = GeckoDarkSurfaceContainer,
    outline = GeckoDarkOutline,
    error = GeckoDarkError,
    onError = GeckoDarkOnError,
)

private val GeckoLightColorScheme = lightColorScheme(
    primary = GeckoLightPrimary,
    onPrimary = GeckoLightOnPrimary,
    primaryContainer = GeckoLightPrimaryContainer,
    onPrimaryContainer = GeckoLightOnPrimaryContainer,
    secondary = GeckoLightSecondary,
    onSecondary = GeckoLightOnSecondary,
    background = GeckoLightBackground,
    onBackground = GeckoLightOnBackground,
    surface = GeckoLightSurface,
    onSurface = GeckoLightOnSurface,
    surfaceVariant = GeckoLightSurfaceVariant,
    onSurfaceVariant = GeckoLightOnSurfaceVariant,
    surfaceContainer = GeckoLightSurfaceContainer,
    outline = GeckoLightOutline,
    error = GeckoLightError,
    onError = GeckoLightOnError,
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
