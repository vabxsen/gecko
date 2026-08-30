package com.gecko.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The gecko silhouette mark used on the launcher icon, re-cut as a tintable [ImageVector] so the
 * same brand glyph can be reused inside the app (empty states, drawer header) instead of a
 * generic Material icon.
 */
val GeckoLogoMark: ImageVector by lazy {
    ImageVector.Builder(
        name = "GeckoLogoMark",
        defaultWidth = 108.dp,
        defaultHeight = 108.dp,
        viewportWidth = 108f,
        viewportHeight = 108f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(54f, 26f)
            curveTo(64f, 26f, 82f, 42f, 82f, 64f)
            curveTo(82f, 72f, 78f, 78f, 72f, 80f)
            curveTo(74f, 74f, 73f, 68f, 69f, 63f)
            curveTo(71f, 70f, 68f, 76f, 61f, 79f)
            curveTo(64f, 72f, 62f, 65f, 56f, 60f)
            curveTo(58f, 68f, 54f, 74f, 46f, 76f)
            curveTo(50f, 68f, 47f, 60f, 40f, 55f)
            curveTo(43f, 63f, 40f, 70f, 33f, 72f)
            curveTo(37f, 63f, 33f, 54f, 26f, 50f)
            curveTo(36f, 44f, 46f, 36f, 54f, 26f)
            close()
        }
    }.build()
}
