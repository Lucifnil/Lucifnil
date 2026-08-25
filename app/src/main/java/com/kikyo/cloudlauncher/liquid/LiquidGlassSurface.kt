/*
 * Uses the same miuix-blur rendering path as SukiSU Ultra's liquid widgets.
 * The screen behind the card is sampled at draw time; lens() applies real
 * AGSL refraction where Android supports it and safely becomes plain blur on
 * older renderers.
 */
package com.kikyo.cloudlauncher.liquid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight

@Composable
internal fun LiquidGlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = 10.dp,
                    color = Color.Black,
                    alpha = 0.10f,
                ),
            )
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(4.dp.toPx(), 4.dp.toPx())
                    lens(
                        refractionHeight = 20.dp.toPx(),
                        refractionAmount = 20.dp.toPx(),
                        chromaticAberration = 0.18f,
                    )
                },
                highlight = { Highlight.Default.copy(alpha = 0.70f) },
                // This is only a tint for text readability. It is not the
                // panel itself: the live sampled scene remains visible.
                onDrawSurface = {
                    drawRect(Color(0xFFEAF7FF).copy(alpha = 0.14f))
                },
            ),
        content = content,
    )
}
