package com.gecko.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.gecko.core.designsystem.theme.GeckoMotion
import com.gecko.core.designsystem.theme.LocalGeckoMotionEnabled

/** A single orbital flourish, then still: no permanent animation on an idle chat. */
@Composable
fun GeckoWelcomeMark(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val motion = LocalGeckoMotionEnabled.current
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val reveal = animateFloatAsState(if (appeared || !motion) 1f else 0f,
        tween(760, easing = GeckoMotion.EasingEmphasized), label = "orbitReveal")
    Box(modifier.size(128.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val progress = if (motion) reveal.value else 1f
            drawCircle(Brush.radialGradient(listOf(ink.copy(alpha = 0.075f), ink.copy(alpha = 0f))), radius = size.minDimension / 2)
            val inset = 8.dp.toPx()
            drawArc(outline.copy(alpha = 0.8f), -65f, 240f * progress, false,
                Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2),
                style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
            val inner = 22.dp.toPx()
            drawArc(outline.copy(alpha = 0.5f), 35f, 135f * progress, false,
                Offset(inner, inner), Size(size.width - inner * 2, size.height - inner * 2),
                style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(ink.copy(alpha = 0.65f * progress), 3.dp.toPx(), Offset(size.width * 0.82f, size.height * 0.8f))
            drawCircle(ink.copy(alpha = 0.25f * progress), 2.dp.toPx(), Offset(size.width * 0.2f, size.height * 0.2f))
        }
        GeckoBrandTile(size = 64.dp, modifier = Modifier.graphicsLayer {
            val progress = if (motion) reveal.value else 1f
            rotationZ = -12f * (1f - progress)
            scaleX = 0.85f + 0.15f * progress
            scaleY = scaleX
        })
    }
}
