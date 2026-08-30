package com.orca.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val OrcaDarkColorScheme = darkColorScheme(
    primary = OrcaDarkPrimary,
    onPrimary = OrcaDarkOnPrimary,
    primaryContainer = OrcaDarkPrimaryContainer,
    onPrimaryContainer = OrcaDarkOnPrimaryContainer,
    secondary = OrcaDarkSecondary,
    onSecondary = OrcaDarkOnSecondary,
    background = OrcaDarkBackground,
    onBackground = OrcaDarkOnBackground,
    surface = OrcaDarkSurface,
    onSurface = OrcaDarkOnSurface,
    surfaceVariant = OrcaDarkSurfaceVariant,
    onSurfaceVariant = OrcaDarkOnSurfaceVariant,
    surfaceContainer = OrcaDarkSurfaceContainer,
    outline = OrcaDarkOutline,
    error = OrcaDarkError,
    onError = OrcaDarkOnError,
)

private val OrcaLightColorScheme = lightColorScheme(
    primary = OrcaLightPrimary,
    onPrimary = OrcaLightOnPrimary,
    primaryContainer = OrcaLightPrimaryContainer,
    onPrimaryContainer = OrcaLightOnPrimaryContainer,
    secondary = OrcaLightSecondary,
    onSecondary = OrcaLightOnSecondary,
    background = OrcaLightBackground,
    onBackground = OrcaLightOnBackground,
    surface = OrcaLightSurface,
    onSurface = OrcaLightOnSurface,
    surfaceVariant = OrcaLightSurfaceVariant,
    onSurfaceVariant = OrcaLightOnSurfaceVariant,
    surfaceContainer = OrcaLightSurfaceContainer,
    outline = OrcaLightOutline,
    error = OrcaLightError,
    onError = OrcaLightOnError,
)

@Composable
fun OrcaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> OrcaDarkColorScheme
        else -> OrcaLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OrcaTypography,
        shapes = OrcaShapes,
        content = content,
    )
}
