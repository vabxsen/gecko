package com.gecko.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

object GeckoMotion {
    const val DURATION_QUICK = 120
    const val DURATION_STANDARD = 220
    const val DURATION_EMPHASIZED = 350

    val EasingStandard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EasingEmphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EasingIncoming: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val EasingOutgoing: Easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
}
