/*
 * Adapted from SukiSU Ultra's InteractiveHighlight (Apache-2.0).
 */
package com.kikyo.cloudlauncher.liquid

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Offset.Companion.VectorConverter
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class InteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val position: (Size, Offset) -> Offset = { _, offset -> offset },
) {
    private val progress = Animatable(0f, 0.001f)
    private val touch = Animatable(Offset.Zero, VectorConverter, Offset(0.1f, 0.1f))
    private var initialTouch = Offset.Zero
    // Keep RuntimeShader in an API-33-only class.  Referencing it directly in
    // this always-loaded composable could make older Android versions verify a
    // class they do not have before the SDK gate can run.
    private val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        InteractiveHighlightShaderApi33()
    } else null

    val modifier: Modifier = Modifier.drawWithContent {
        val amount = progress.value
        if (amount > 0f) {
            drawRect(Color.White.copy(alpha = 0.06f * amount), blendMode = BlendMode.Plus)
            shader?.let { runtime ->
                val p = position(size, touch.value)
                drawRect(
                    runtime.brush(
                        size = size,
                        color = Color.White.copy(alpha = 0.12f * amount),
                        radius = size.minDimension * 1.2f,
                        position = Offset(
                            p.x.fastCoerceIn(0f, size.width),
                            p.y.fastCoerceIn(0f, size.height),
                        ),
                    ),
                    blendMode = BlendMode.Plus,
                )
            }
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                initialTouch = down.position
                animationScope.launch {
                    launch { progress.animateTo(1f, spring(0.5f, 300f, 0.001f)) }
                    launch { touch.snapTo(initialTouch) }
                }
            },
            onDragEnd = { release() },
            onDragCancel = { release() },
        ) { change, _ -> animationScope.launch { touch.snapTo(change.position) } }
    }

    private fun release() {
        animationScope.launch {
            launch { progress.animateTo(0f, spring(0.5f, 300f, 0.001f)) }
            launch { touch.animateTo(initialTouch, spring(0.5f, 300f, Offset(0.1f, 0.1f))) }
        }
    }
}
