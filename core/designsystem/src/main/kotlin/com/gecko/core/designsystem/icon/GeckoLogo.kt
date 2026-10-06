package com.gecko.core.designsystem.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.gecko.core.designsystem.R

/** The same tintable vector used by the adaptive launcher icon. */
@Composable
fun geckoLogoPainter(): Painter = painterResource(R.drawable.gecko_monogram)
