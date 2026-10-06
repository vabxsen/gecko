package com.gecko.core.designsystem.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val LocalGeckoMotionEnabled = compositionLocalOf { true }

/** Follow Android's animation switch, including changes made while the app is open. */
@Composable
internal fun rememberSystemMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun readSetting() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    var enabled by remember { mutableStateOf(readSetting()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            // Read the setting itself; ValueAnimator's separate observer may not have updated yet.
            override fun onChange(selfChange: Boolean) { enabled = readSetting() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        enabled = readSetting()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

/** Draw-only transforms keep spring feedback from changing layout or the touch target. */
@Composable
fun Modifier.geckoPress(source: MutableInteractionSource, enabled: Boolean = true): Modifier {
    val pressed by source.collectIsPressedAsState()
    val motion = LocalGeckoMotionEnabled.current
    val scale = animateFloatAsState(if (pressed && enabled && motion) 0.96f else 1f,
        spring(dampingRatio = 0.72f, stiffness = 650f), label = "pressScale")
    return graphicsLayer {
        scaleX = if (motion) scale.value else 1f
        scaleY = scaleX
    }
}

/** One entrance per composition; no timers or animation restarts on text updates. */
@Composable
fun Modifier.geckoReveal(delayMillis: Int = 0): Modifier {
    val motion = LocalGeckoMotionEnabled.current
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val progress = animateFloatAsState(if (appeared || !motion) 1f else 0f,
        tween(440, delayMillis = delayMillis, easing = GeckoMotion.EasingEmphasized), label = "entrance")
    return graphicsLayer {
        val fraction = if (motion) progress.value else 1f
        alpha = fraction
        translationY = 16.dp.toPx() * (1f - fraction)
        scaleX = 0.98f + fraction * 0.02f
        scaleY = scaleX
    }
}

/** A short, centered spring entrance for transient surfaces such as dialogs. */
@Composable
fun Modifier.geckoPopIn(): Modifier {
    val motion = LocalGeckoMotionEnabled.current
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val progress = animateFloatAsState(if (appeared || !motion) 1f else 0f,
        spring(dampingRatio = 0.82f, stiffness = 550f), label = "surfaceEntrance")
    return graphicsLayer {
        val fraction = if (motion) progress.value else 1f
        alpha = fraction.coerceIn(0f, 1f)
        scaleX = 0.94f + fraction * 0.06f
        scaleY = scaleX
    }
}
