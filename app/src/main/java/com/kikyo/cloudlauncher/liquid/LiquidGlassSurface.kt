/*
 * Built on the same Backdrop/Capsule liquid renderer used by SukiSU Ultra's
 * API-36 implementation. This is a live sampled surface, not a translucent
 * white rectangle: blur, vibrancy and lens() are applied to the page behind it.
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

@Composable
internal fun LiquidGlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(8.dp.toPx())
                lens(20.dp.toPx(), 20.dp.toPx())
            },
            highlight = { Highlight.Default.copy(alpha = 0.82f) },
            shadow = {
                Shadow.Default.copy(color = Color.Black.copy(alpha = 0.10f))
            },
            innerShadow = {
                InnerShadow(radius = 6.dp, alpha = 0.20f)
            },
            // A low-opacity readability tint only; the page remains sampled
            // and refracted through the surface.
            onDrawSurface = {
                drawRect(Color(0xFFEAF7FF).copy(alpha = 0.09f))
            },
        ),
        content = content,
    )
}
