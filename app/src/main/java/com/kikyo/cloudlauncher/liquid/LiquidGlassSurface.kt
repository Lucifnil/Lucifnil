/*
 * Reuses the native Backdrop/Capsule stack used by SukiSU Ultra.  This is a
 * live sampled surface: the pixels behind each panel are blurred, refracted
 * and highlighted at draw time instead of being simulated with a white
 * translucent gradient.
 */
package com.kikyo.cloudlauncher.liquid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule

@Composable
internal fun LiquidGlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = ContinuousCapsule,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(7.dp.toPx())
                lens(18.dp.toPx(), 18.dp.toPx())
            },
            highlight = { Highlight.Default.copy(alpha = 0.72f) },
            shadow = { Shadow.Default.copy(color = Color.Black.copy(alpha = 0.12f)) },
            innerShadow = { InnerShadow(radius = 6.dp, alpha = 0.28f) },
            // A small cool tint preserves legibility while the sampled scene,
            // rather than a white rectangle, remains the visible material.
            onDrawSurface = {
                drawRect(Color(0xFFEAF7FF).copy(alpha = 0.075f))
            },
        ),
        content = content,
    )
}
