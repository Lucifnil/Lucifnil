/*
 * Directly based on SukiSU Ultra's InteractiveHighlight (Apache-2.0).
 * The API guard preserves the same live press bloom on Android 13+ while
 * preventing a RuntimeShader class-load crash on older Android releases.
 */
package com.kikyo.cloudlauncher.liquid

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@SuppressLint("NewApi")
internal class InteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
) {
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var startPosition = Offset.Zero

    // RuntimeShader exists from Android 13 (API 33). SukiSU's original code
    // uses this shader directly; older devices receive the safe radial fallback.
    private val shader: RuntimeShader? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(PRESS_HIGHLIGHT_SHADER)
        } else {
            null
        }

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            drawRect(
                Color.White.copy(alpha = 0.06f * progress),
                blendMode = BlendMode.Plus,
            )
            val activeShader = shader
            val lightPosition = position(size, positionAnimation.value)
            if (activeShader != null) {
                activeShader.apply {
                    setFloatUniform("size", size.width, size.height)
                    setColorUniform("color", Color.White.copy(alpha = 0.12f * progress).toArgb())
                    setFloatUniform("radius", size.minDimension * 1.2f)
                    setFloatUniform(
                        "position",
                        lightPosition.x.fastCoerceIn(0f, size.width),
                        lightPosition.y.fastCoerceIn(0f, size.height),
                    )
                }
                drawRect(ShaderBrush(activeShader), blendMode = BlendMode.Plus)
            } else {
                drawCircle(
                    color = Color.White.copy(alpha = 0.10f * progress),
                    radius = size.minDimension * 0.60f,
                    center = lightPosition,
                    blendMode = BlendMode.Plus,
                )
            }
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch {
                        pressProgressAnimation.animateTo(1f, spring(0.5f, 300f, 0.001f))
                    }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch {
                        pressProgressAnimation.animateTo(0f, spring(0.5f, 300f, 0.001f))
                    }
                    launch {
                        positionAnimation.animateTo(
                            startPosition,
                            spring(0.5f, 300f, Offset.VisibilityThreshold),
                        )
                    }
                }
            },
            onDragCancel = {
                animationScope.launch {
                    launch {
                        pressProgressAnimation.animateTo(0f, spring(0.5f, 300f, 0.001f))
                    }
                    launch {
                        positionAnimation.animateTo(
                            startPosition,
                            spring(0.5f, 300f, Offset.VisibilityThreshold),
                        )
                    }
                }
            },
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }

    private companion object {
        private const val PRESS_HIGHLIGHT_SHADER = """
            uniform float2 size;
            layout(color) uniform half4 color;
            uniform float radius;
            uniform float2 position;

            half4 main(float2 coord) {
                float dist = distance(coord, position);
                float intensity = smoothstep(radius, radius * 0.5, dist);
                return color * intensity;
            }
        """
    }
}
