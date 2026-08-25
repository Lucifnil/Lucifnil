/*
 * Direct adaptation of SukiSU Ultra's original FloatingBottomBar.
 * Sources: SukiSU Ultra commit 3085859 and Kyant0/AndroidLiquidGlass.
 * Apache-2.0. The caller only supplies CloudLauncher colours and tab content.
 */
package com.kikyo.cloudlauncher.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

@Composable
internal fun RowScope.LiquidBottomBarItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(ContinuousCapsule)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .fillMaxHeight()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
internal fun SukiLiquidBottomBar(
    selectedIndex: () -> Int,
    onSelected: (Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    // The reference draws a low-alpha surface *over* live sampled pixels. It
    // is deliberately not a white gradient card.
    val containerColor = Color(0xFFEAF7FF).copy(alpha = 0.10f)
    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val scope = rememberCoroutineScope()
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    val offsetAnimation = remember { Animatable(0f) }
    val panelOffset by remember(density) {
        derivedStateOf {
            if (totalWidthPx == 0f) 0f else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }
    }

    var currentIndex by remember { mutableIntStateOf(selectedIndex()) }
    class Holder { var value: DampedDragAnimation? = null }
    val holder = remember { Holder() }
    val drag = remember(scope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = selectedIndex().toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { offset ->
                val animation = holder.value ?: return@DampedDragAnimation true
                if (tabWidthPx == 0f) return@DampedDragAnimation false
                val indicatorX = animation.value * tabWidthPx
                val padding = with(density) { 4.dp.toPx() }
                val globalTouchX = if (isLtr) padding + indicatorX + offset.x
                else totalWidthPx - padding - tabWidthPx - indicatorX + offset.x
                globalTouchX in 0f..totalWidthPx
            },
            onDragStarted = {},
            onDragStopped = {
                val target = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                currentIndex = target
                animateToValue(target.toFloat())
                scope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    scope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                }
            },
        ).also { holder.value = it }
    }

    LaunchedEffect(selectedIndex()) { currentIndex = selectedIndex() }
    LaunchedEffect(drag) {
        snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
            drag.animateToValue(index.toFloat())
            onSelected(index)
        }
    }

    val highlight = remember(scope, tabWidthPx) {
        InteractiveHighlight(scope) { size, _ ->
            Offset(
                if (isLtr) (drag.value + 0.5f) * tabWidthPx + panelOffset
                else size.width - (drag.value + 0.5f) * tabWidthPx + panelOffset,
                size.height / 2f,
            )
        }
    }

    Box(modifier = modifier.width(IntrinsicSize.Min), contentAlignment = Alignment.CenterStart) {
        // Main lens: captures the actual page, not a re-created gradient.
        Row(
            Modifier
                .onGloballyPositioned { coordinates ->
                    totalWidthPx = coordinates.size.width.toFloat()
                    val contentWidth = totalWidthPx - with(density) { 8.dp.toPx() }
                    tabWidthPx = (contentWidth / tabsCount).coerceAtLeast(0f)
                }
                .graphicsLayer { translationX = panelOffset }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousCapsule },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    highlight = { Highlight.Default.copy(alpha = 1f) },
                    shadow = { Shadow.Default.copy(color = Color.Black.copy(alpha = 0.10f)) },
                    layerBlock = {
                        val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, drag.pressProgress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .then(highlight.modifier)
                .height(64.dp)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        // Suki's second captured tab layer; the moving lens receives both the
        // background and tab content via rememberCombinedBackdrop below.
        Row(
            Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousCapsule },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(24.dp.toPx() * drag.pressProgress, 24.dp.toPx() * drag.pressProgress)
                    },
                    highlight = { Highlight.Default.copy(alpha = drag.pressProgress) },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .then(highlight.modifier)
                .height(56.dp)
                .padding(horizontal = 4.dp)
                .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        if (tabWidthPx > 0f) {
            // Selected lens: actual refraction, chromatic/depth-ready lens,
            // pressure expansion and velocity deformation copied from Suki.
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .graphicsLayer {
                        val offset = drag.value * tabWidthPx
                        translationX = if (isLtr) offset + panelOffset else -offset + panelOffset
                    }
                    .then(highlight.gestureModifier)
                    .then(drag.modifier)
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                        shape = { ContinuousCapsule },
                        effects = {
                            lens(
                                10.dp.toPx() * drag.pressProgress,
                                14.dp.toPx() * drag.pressProgress,
                                true,
                            )
                        },
                        highlight = { Highlight.Default.copy(alpha = drag.pressProgress) },
                        shadow = { Shadow(alpha = drag.pressProgress) },
                        innerShadow = {
                            InnerShadow(
                                radius = 8.dp * drag.pressProgress,
                                alpha = drag.pressProgress,
                            )
                        },
                        layerBlock = {
                            scaleX = drag.scaleX
                            scaleY = drag.scaleY
                            val velocity = drag.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            drawRect(Color.Black.copy(alpha = 0.10f), alpha = 1f - drag.pressProgress)
                            drawRect(Color.Black.copy(alpha = 0.03f * drag.pressProgress))
                        },
                    )
                    .height(56.dp)
                    .width(with(density) { ((totalWidthPx - 8.dp.toPx()) / tabsCount).toDp() }),
            )
        }
    }
}
