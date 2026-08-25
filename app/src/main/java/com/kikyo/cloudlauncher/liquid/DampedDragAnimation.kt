/*
 * Adapted from SukiSU Ultra's DampedDragAnimation (Apache-2.0).
 */
package com.kikyo.cloudlauncher.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

internal class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedRange<Float>,
    visibilityThreshold: Float,
    private val initialScale: Float,
    pressedScale: Float,
    private val canDrag: (Offset) -> Boolean = { true },
    private val onDragStarted: DampedDragAnimation.(Offset) -> Unit,
    private val onDragStopped: DampedDragAnimation.() -> Unit,
    private val onDrag: DampedDragAnimation.(IntSize, Offset) -> Unit,
) {
    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)
    private val pressedScale = pressedScale
    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down -> onDragStarted(down.position); press() },
            onDragEnd = { onDragStopped(); release() },
            onDragCancel = { onDragStopped(); release() },
        ) { change, delta ->
            if (canDrag(change.position) && canDrag(change.previousPosition)) {
                onDrag(size, delta)
            }
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, spring(1f, 1000f, 0.001f)) }
            launch { scaleXAnimation.animateTo(pressedScale, spring(0.6f, 250f, 0.001f)) }
            launch { scaleYAnimation.animateTo(pressedScale, spring(0.7f, 250f, 0.001f)) }
        }
    }

    fun release() {
        animationScope.launch {
            awaitFrame()
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, spring(1f, 1000f, 0.001f)) }
            launch { scaleXAnimation.animateTo(initialScale, spring(0.6f, 250f, 0.001f)) }
            launch { scaleYAnimation.animateTo(initialScale, spring(0.7f, 250f, 0.001f)) }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch {
            valueAnimation.animateTo(target, spring(1f, 1000f, 0.001f)) { updateVelocity() }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val target = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(target, spring(1f, 1000f, 0.001f)) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, spring(0.5f, 300f, 0.01f)) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(System.currentTimeMillis(), Offset(value, 0f))
        val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.001f)
        val target = velocityTracker.calculateVelocity().x / span
        animationScope.launch { velocityAnimation.animateTo(target, spring(0.5f, 300f, 0.01f)) }
    }
}
