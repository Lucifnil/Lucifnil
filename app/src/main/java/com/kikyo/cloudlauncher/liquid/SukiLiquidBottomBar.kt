/*
 * Directly derived from SukiSU Ultra's FloatingBottomBar (Apache-2.0).
 * It uses the exact miuix-blur capture/lens composition: one live page
 * backdrop, one tab-content backdrop, and their combined moving lens.
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import kotlin.math.abs
import kotlin.math.sign

private val SukiSpecularHighlight = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)

@Composable
internal fun RowScope.LiquidBottomBarItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
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
    val containerColor = Color(0xFFEAF7FF).copy(alpha = 0.20f)
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val scope = rememberCoroutineScope()
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) 0f else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    var currentIndex by remember { mutableIntStateOf(selectedIndex()) }
    class Holder { var instance: DampedDragAnimation? = null }
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
                val animation = holder.instance ?: return@DampedDragAnimation true
                if (tabWidthPx == 0f) return@DampedDragAnimation false
                val indicatorX = animation.value * tabWidthPx
                val padding = with(density) { 4.dp.toPx() }
                val globalX = if (isLtr) padding + indicatorX + offset.x else {
                    totalWidthPx - padding - tabWidthPx - indicatorX + offset.x
                }
                globalX in 0f..totalWidthPx
            },
            onDragStarted = {},
            onDragStopped = {
                val target = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                currentIndex = target
                animateToValue(target.toFloat())
                scope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
            },
            onDrag = { _, amount ->
                if (tabWidthPx > 0f) {
                    updateValue(
                        (targetValue + amount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    scope.launch { offsetAnimation.snapTo(offsetAnimation.value + amount.x) }
                }
            },
        ).also { holder.instance = it }
    }

    LaunchedEffect(selectedIndex()) { currentIndex = selectedIndex() }
    LaunchedEffect(drag) {
        snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
            drag.animateToValue(index.toFloat())
            onSelected(index)
        }
    }

    Box(modifier = modifier.width(IntrinsicSize.Min), contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier
                .onGloballyPositioned { coordinates ->
                    totalWidthPx = coordinates.size.width.toFloat()
                    val contentWidth = totalWidthPx - with(density) { 8.dp.toPx() }
                    tabWidthPx = (contentWidth / tabsCount).coerceAtLeast(0f)
                }
                .graphicsLayer { translationX = panelOffset }
                .dropShadow(
                    shape = CircleShape,
                    shadow = Shadow(radius = 10.dp, color = Color.Black, alpha = 0.10f),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx(), 4.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    highlight = { SukiSpecularHighlight.copy(alpha = 0.75f) },
                    layerBlock = {
                        val width = size.width.coerceAtLeast(1f)
                        val scale = lerp(1f, 1f + 16.dp.toPx() / width, drag.pressProgress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .height(64.dp)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        // This invisible capture contains the tabs. The moving glass below
        // refracts it together with the live page backdrop, just like SukiSU.
        Row(
            Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx(), 4.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    highlight = { SukiSpecularHighlight.copy(alpha = 0.45f) },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        if (tabWidthPx > 0f) {
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .graphicsLayer {
                        val x = drag.value * tabWidthPx
                        translationX = if (isLtr) x + panelOffset else -x + panelOffset
                    }
                    .then(drag.modifier)
                    .drawBackdrop(
                        backdrop = combinedBackdrop,
                        shape = { CircleShape },
                        effects = {
                            val progress = drag.pressProgress
                            lens(
                                refractionHeight = 10.dp.toPx() * progress,
                                refractionAmount = 14.dp.toPx() * progress,
                                depthEffect = true,
                                chromaticAberration = 0.5f,
                            )
                        },
                        highlight = { SukiSpecularHighlight.copy(alpha = drag.pressProgress) },
                        layerBlock = {
                            scaleX = drag.scaleX
                            scaleY = drag.scaleY
                            val velocity = drag.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            drawRect(Color.Black.copy(alpha = 0.10f), alpha = 1f - drag.pressProgress)
                            drawRect(accentColor.copy(alpha = 0.06f * drag.pressProgress))
                        },
                    )
                    .innerShadow(shape = CircleShape) {
                        InnerShadow(
                            radius = 8.dp * drag.pressProgress,
                            color = Color.Black.copy(alpha = 0.15f),
                            alpha = drag.pressProgress,
                        )
                    }
                    .height(56.dp)
                    .width(with(density) { ((totalWidthPx - 8.dp.toPx()) / tabsCount).toDp() }),
            )
        }
    }
}
