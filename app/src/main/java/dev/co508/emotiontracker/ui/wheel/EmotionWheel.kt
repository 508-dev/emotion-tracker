package dev.co508.emotiontracker.ui.wheel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.co508.emotiontracker.data.EmotionNode
import dev.co508.emotiontracker.ui.parsedColor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.unit.min as minDp

// The palette (see emotion_tree.json) is deliberately soft/pastel, so a
// fixed dark, matte label color reads better across all of it than white
// (which the old, more saturated palette needed).
private val LabelColor = Color(0xFF3A3733)

/** Slow debug duration for inspecting the inside-out roll between levels. */
private const val ROLL_DURATION_MS = 1_600

/** A tappable region of the wheel: the center "save" hub, or one of the current level's wedges. */
private sealed interface Zone {
    data object Hub : Zone

    data class Wedge(
        val index: Int,
    ) : Zone
}

/** Pixel geometry of the wheel's concentric parts, shared by every level drawn during a transition. */
private data class WheelGeometry(
    val center: Offset,
    val radiusPx: Float,
    val hubRadiusPx: Float,
    val ringInnerRadiusPx: Float,
)

private data class LeafMorph(
    val parent: EmotionNode,
    val leaf: EmotionNode,
    val wedgeIndex: Int,
)

/**
 * The emotion wheel: a center hub always names whatever level is currently
 * on screen (empty at the root, since there's nothing to save yet) and
 * tapping it saves that level. The current level's children — if any — fill
 * the rest of the disk as equal wedges reaching all the way to the outer
 * edge; tapping one drills one level deeper. There's no memory of ancestor
 * levels drawn as rings — that history lives in the breadcrumbs above the
 * wheel instead (see EmotionWheelScreen).
 *
 * Moving between levels plays a short "roll": drilling in, the new level
 * grows out of the center over the old one — like twisting a donut so its
 * inside becomes the outside. The metaphor lands because the tapped wedge's
 * emotion *becomes* the new hub, so its color genuinely does spill into the
 * center and unfurl. Backing out plays the same move in reverse: the deeper
 * level rolls back into the center and away, revealing the shallower one.
 */
@Composable
fun EmotionWheel(
    path: List<EmotionNode>,
    onSelect: (EmotionNode) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    // Keep the outgoing level around for one roll whenever the path changes.
    // (Writing state during composition is deliberate here: the writes
    // converge on the very next recomposition — the documented pattern for
    // "remember the previous value of an input".)
    var displayedPath by remember { mutableStateOf(path) }
    var outgoingPath by remember { mutableStateOf<List<EmotionNode>?>(null) }
    var rollStarted by remember { mutableStateOf(false) }
    if (displayedPath != path) {
        outgoingPath = displayedPath
        displayedPath = path
        rollStarted = false
    }
    val roll = remember { Animatable(1f) }
    LaunchedEffect(displayedPath) {
        if (outgoingPath != null) {
            roll.snapTo(0f)
            rollStarted = true
            // No finally/cleanup on cancellation: if a newer path arrives
            // mid-roll, the newer composition has already replaced
            // outgoingPath and owns the rest of the animation.
            roll.animateTo(1f, tween(durationMillis = ROLL_DURATION_MS, easing = FastOutSlowInEasing))
            outgoingPath = null
            rollStarted = false
        }
    }

    val current = displayedPath.last()
    val children = current.children
    val canSave = displayedPath.size > 1
    val wedgeAngle = if (children.isNotEmpty()) 360f / children.size else 0f

    var pressedZone by remember(current.id) { mutableStateOf<Zone?>(null) }
    val pressProgress = remember(current.id) { Animatable(0f) }

    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val density = LocalDensity.current
        val sizePx = with(density) { minDp(maxWidth, maxHeight).toPx() }
        val radiusPx = sizePx / 2f
        val geometry =
            WheelGeometry(
                center = Offset(radiusPx, radiusPx),
                radiusPx = radiusPx,
                hubRadiusPx = radiusPx * 0.32f,
                ringInnerRadiusPx = radiusPx * 0.32f + radiusPx * 0.03f,
            )

        fun zoneFor(offset: Offset): Zone? {
            val dist = hypot(offset.x - geometry.center.x, offset.y - geometry.center.y)
            return when {
                canSave && children.isEmpty() && dist <= radiusPx -> Zone.Hub
                dist <= geometry.hubRadiusPx -> if (canSave) Zone.Hub else null
                dist <= radiusPx && children.isNotEmpty() -> {
                    val angleDeg =
                        Math
                            .toDegrees(
                                atan2(offset.y - geometry.center.y, offset.x - geometry.center.x).toDouble(),
                            ).toFloat()
                    val relativeDeg = ((angleDeg + 90f) % 360f + 360f) % 360f
                    Zone.Wedge((relativeDeg / wedgeAngle).toInt().coerceIn(0, children.size - 1))
                }
                else -> null
            }
        }

        Box(Modifier.fillMaxSize()) {
            val outgoing = outgoingPath
            if (outgoing == null) {
                WheelLevel(
                    current = current,
                    canSave = canSave,
                    pressedZone = pressedZone,
                    pressProgress = pressProgress,
                    geometry = geometry,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Two levels on screen at once for the roll. Drilling in, the
                // new (deeper) level is on top, revealed by a growing circular
                // clip; backing out, the old (deeper) level is on top and the
                // clip shrinks it into the center. Roll state is read only
                // inside graphicsLayer/draw blocks so animating it redraws
                // instead of recomposing the wheel every frame.
                val drilling = displayedPath.size > outgoing.size
                val topPath = if (drilling) displayedPath else outgoing
                val bottomPath = if (drilling) outgoing else displayedPath
                val leafMorph =
                    leafMorph(
                        parentPath = if (drilling) outgoing else displayedPath,
                        leafPath = if (drilling) displayedPath else outgoing,
                    )

                WheelLevel(
                    current = bottomPath.last(),
                    canSave = bottomPath.size > 1,
                    pressedZone = if (drilling) null else pressedZone,
                    pressProgress = pressProgress,
                    geometry = geometry,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val t = rollProgress(rollStarted, roll.value)
                                // Keep the settled wheel size fixed; the depth
                                // cue belongs to the folded lip, not to the
                                // whole old level expanding under it.
                                alpha =
                                    when {
                                        leafMorph != null -> 1f
                                        drilling -> 1f - 0.18f * t
                                        else -> 0.82f + 0.18f * t
                                    }
                                shape = CircleShape
                                clip = true
                            },
                )

                if (leafMorph == null) {
                    WheelLevel(
                        current = topPath.last(),
                        canSave = topPath.size > 1,
                        pressedZone = if (drilling) pressedZone else null,
                        pressProgress = pressProgress,
                        geometry = geometry,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val t = rollProgress(rollStarted, roll.value)
                                    val frac = if (drilling) t else 1f - t
                                    alpha = ((frac - 0.04f) / 0.16f).coerceIn(0f, 1f)
                                    shape = CircleClipShape(geometry.radiusPx * frac)
                                    clip = true
                                },
                    )
                    // The rolling boundary itself. It is drawn as a shaded fold,
                    // not a flat outline, so the reveal reads more like a soft
                    // donut turning through itself.
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                shape = CircleShape
                                clip = true
                            },
                    ) {
                        val t = rollProgress(rollStarted, roll.value)
                        drawInsideOutFold(
                            geometry = geometry,
                            progress = if (drilling) t else 1f - t,
                            turn = t,
                            color = topPath.last().parsedColor,
                            drilling = drilling,
                        )
                    }
                } else {
                    val t = rollProgress(rollStarted, roll.value)
                    LeafMorphLevel(
                        morph = leafMorph,
                        progress = if (drilling) t else 1f - t,
                        geometry = geometry,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Hit testing always uses the settled geometry of the displayed
            // level, so taps land even mid-roll.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(current.id) {
                        detectTapGestures(
                            onPress = { offset ->
                                val zone = zoneFor(offset) ?: return@detectTapGestures
                                pressedZone = zone
                                coroutineScope {
                                    // Press in on a side coroutine: lifting the
                                    // finger cancels it instantly and the
                                    // rebound starts from wherever it got to,
                                    // instead of making a quick tap wait out
                                    // the full depress animation first.
                                    val pressIn = launch { pressProgress.animateTo(1f, tween(110)) }
                                    val released = tryAwaitRelease()
                                    pressIn.cancel()
                                    if (released) {
                                        when (zone) {
                                            Zone.Hub -> {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onSave()
                                            }
                                            is Zone.Wedge -> {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onSelect(children[zone.index])
                                            }
                                        }
                                    }
                                    pressProgress.animateTo(0f, tween((160f * pressProgress.value).roundToInt()))
                                }
                                pressedZone = null
                            },
                        )
                    },
            )
        }
    }
}

/**
 * One level of the wheel: the hub naming [current] (tap-to-save), a soft halo
 * bridging hub and ring, and [current]'s children as equal wedges. Drawn
 * standalone when settled, and twice (outgoing + incoming) while a roll
 * transition is running.
 */
@Composable
private fun WheelLevel(
    current: EmotionNode,
    canSave: Boolean,
    pressedZone: Zone?,
    pressProgress: Animatable<Float, AnimationVector1D>,
    geometry: WheelGeometry,
    modifier: Modifier = Modifier,
) {
    val children = current.children
    val wedgeAngle = if (children.isNotEmpty()) 360f / children.size else 0f
    val density = LocalDensity.current
    val (center, radiusPx, hubRadiusPx, ringInnerRadiusPx) = geometry
    val isLeafSave = canSave && children.isEmpty()
    val labelRadiusPx = if (isLeafSave) radiusPx else hubRadiusPx
    val hubLabelWidthDp = with(density) { (labelRadiusPx * 1.5f).toDp() }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // A soft halo bridging the hub and the ring, so the boundary
            // between them reads as a gradient rather than a hard seam.
            if (!isLeafSave) {
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(current.parsedColor.copy(alpha = 0.45f), Color.Transparent),
                            center = center,
                            radius = ringInnerRadiusPx,
                        ),
                    radius = ringInnerRadiusPx,
                    center = center,
                )
            }

            val hubPressT = if (pressedZone == Zone.Hub) pressProgress.value else 0f
            drawCircle(
                color = current.parsedColor,
                radius = labelRadiusPx * (1f - 0.08f * hubPressT),
                center = center,
            )

            children.forEachIndexed { i, child ->
                val wedgePressT = if (pressedZone == Zone.Wedge(i)) pressProgress.value else 0f
                val outerRadius = radiusPx * (1f - 0.05f * wedgePressT)
                val startAngle = -90f + i * wedgeAngle
                drawPath(
                    path = annularWedgePath(center, ringInnerRadiusPx, outerRadius, startAngle, wedgeAngle),
                    color = child.parsedColor,
                )
            }
            if (children.size > 1) {
                children.indices.forEach { i ->
                    drawSoftDivider(center, ringInnerRadiusPx, radiusPx, -90f + i * wedgeAngle)
                }
            }
        }

        if (canSave) {
            Text(
                text = current.label,
                color = LabelColor,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).width(hubLabelWidthDp),
            )
        }

        val labelWidthDp = 84.dp
        val labelWidthPx = with(density) { labelWidthDp.toPx() }
        children.forEachIndexed { i, child ->
            val midAngleDeg = -90f + (i + 0.5f) * wedgeAngle
            val midAngleRad = Math.toRadians(midAngleDeg.toDouble())
            val labelRadiusPx = (ringInnerRadiusPx + radiusPx) / 2f
            val xPx = radiusPx + labelRadiusPx * cos(midAngleRad).toFloat()
            val yPx = radiusPx + labelRadiusPx * sin(midAngleRad).toFloat()
            val offsetX = with(density) { (xPx - labelWidthPx / 2f).toDp() }
            val offsetY = with(density) { (yPx - 10.dp.toPx()).toDp() }

            Box(modifier = Modifier.offset(x = offsetX, y = offsetY).width(labelWidthDp)) {
                Text(
                    text = child.label,
                    color = LabelColor,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(labelWidthDp),
                )
            }
        }
    }
}

/**
 * A circle of animated radius centered in the layout, used to clip the level
 * that's rolling in (or out): the clip edge *is* the rolling boundary, so
 * this shape is what makes the "inside becomes the outside" read work.
 */
private data class CircleClipShape(
    private val radiusPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path().apply { addOval(Rect(center = size.center, radius = radiusPx)) }
        return Outline.Generic(path)
    }
}

/** A ring sector from [innerRadius] to [outerRadius], spanning [sweepDeg] degrees from [startAngleDeg]. */
private fun annularWedgePath(
    center: Offset,
    innerRadius: Float,
    outerRadius: Float,
    startAngleDeg: Float,
    sweepDeg: Float,
): Path {
    val startRad = Math.toRadians(startAngleDeg.toDouble())
    val endRad = Math.toRadians((startAngleDeg + sweepDeg).toDouble())
    return Path().apply {
        moveTo(center.x + innerRadius * cos(startRad).toFloat(), center.y + innerRadius * sin(startRad).toFloat())
        lineTo(center.x + outerRadius * cos(startRad).toFloat(), center.y + outerRadius * sin(startRad).toFloat())
        arcTo(Rect(center = center, radius = outerRadius), startAngleDeg, sweepDeg, forceMoveTo = false)
        lineTo(center.x + innerRadius * cos(endRad).toFloat(), center.y + innerRadius * sin(endRad).toFloat())
        arcTo(Rect(center = center, radius = innerRadius), startAngleDeg + sweepDeg, -sweepDeg, forceMoveTo = false)
        close()
    }
}

private fun leafMorph(
    parentPath: List<EmotionNode>,
    leafPath: List<EmotionNode>,
): LeafMorph? {
    val leaf = leafPath.last()
    if (leaf.children.isNotEmpty()) return null

    val parent = parentPath.last()
    val wedgeIndex = parent.children.indexOfFirst { it.id == leaf.id }
    if (wedgeIndex < 0) return null

    return LeafMorph(parent = parent, leaf = leaf, wedgeIndex = wedgeIndex)
}

@Composable
private fun LeafMorphLevel(
    morph: LeafMorph,
    progress: Float,
    geometry: WheelGeometry,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val labelWidthDp = with(density) { (geometry.hubRadiusPx * 1.5f).toDp() }
    val labelAlpha = ((progress - 0.72f) / 0.18f).coerceIn(0f, 1f)

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLeafMorph(morph = morph, progress = progress, geometry = geometry)
        }
        if (labelAlpha > 0f) {
            Text(
                text = morph.leaf.label,
                color = LabelColor.copy(alpha = labelAlpha),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).width(labelWidthDp),
            )
        }
    }
}

private fun DrawScope.drawLeafMorph(
    morph: LeafMorph,
    progress: Float,
    geometry: WheelGeometry,
) {
    val t = eased(progress.coerceIn(0f, 1f))
    val parentChildren = morph.parent.children
    val wedgeAngle = 360f / parentChildren.size
    val startAngle = -90f + morph.wedgeIndex * wedgeAngle
    val midAngle = startAngle + wedgeAngle / 2f
    val midAngleRad = Math.toRadians(midAngle.toDouble())
    val sourceRadius = (geometry.ringInnerRadiusPx + geometry.radiusPx) / 2f
    val sourceCenter =
        Offset(
            x = geometry.center.x + sourceRadius * cos(midAngleRad).toFloat(),
            y = geometry.center.y + sourceRadius * sin(midAngleRad).toFloat(),
        )
    val circleCenter = lerp(sourceCenter, geometry.center, t)
    val circleRadius = lerp(geometry.radiusPx * 0.16f, geometry.radiusPx, t)
    val sliceAlpha = (1f - t).coerceIn(0f, 1f)
    val color = morph.leaf.parsedColor

    drawPath(
        path =
            annularWedgePath(
                center = geometry.center,
                innerRadius = geometry.ringInnerRadiusPx,
                outerRadius = geometry.radiusPx,
                startAngleDeg = startAngle,
                sweepDeg = wedgeAngle,
            ),
        color = color.copy(alpha = sliceAlpha),
    )
    drawCircle(
        color = Color.Black.copy(alpha = 0.12f * t),
        radius = circleRadius * 1.04f,
        center = circleCenter.copy(y = circleCenter.y + 5.dp.toPx() * t),
    )
    drawCircle(
        color = color,
        radius = circleRadius,
        center = circleCenter,
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.18f * t),
        radius = circleRadius * 0.74f,
        center =
            circleCenter.copy(
                x = circleCenter.x - circleRadius * 0.18f,
                y = circleCenter.y - circleRadius * 0.2f,
            ),
    )
}

/**
 * Draws a wedge boundary as a few overlaid, low-alpha strokes of increasing
 * width instead of one crisp line — a cheap stand-in for a real blur that
 * keeps slice edges soft without needing a minSdk-gated RenderEffect.
 */
private fun DrawScope.drawSoftDivider(
    center: Offset,
    innerRadius: Float,
    outerRadius: Float,
    angleDeg: Float,
) {
    val angleRad = Math.toRadians(angleDeg.toDouble())
    val dx = cos(angleRad).toFloat()
    val dy = sin(angleRad).toFloat()
    val start = Offset(center.x + innerRadius * dx, center.y + innerRadius * dy)
    val end = Offset(center.x + outerRadius * dx, center.y + outerRadius * dy)
    listOf(8.dp to 0.03f, 4.dp to 0.05f, 1.5.dp to 0.09f).forEach { (width, alpha) ->
        drawLine(
            color = Color.Black.copy(alpha = alpha),
            start = start,
            end = end,
            strokeWidth = width.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Paints the moving reveal edge as a turned-over lip. The wheel contents stay
 * geometrically flat and full-sized; this fold supplies the illusion of depth
 * with a cast shadow, colored side wall, top highlight, and lower occlusion.
 */
private fun DrawScope.drawInsideOutFold(
    geometry: WheelGeometry,
    progress: Float,
    turn: Float,
    color: Color,
    drilling: Boolean,
) {
    val foldT = sin(turn * PI.toFloat()).coerceIn(0f, 1f)
    if (progress <= 0.01f || progress >= 0.995f || foldT <= 0.01f) return

    val radius = geometry.radiusPx * progress
    val foldWidth = (geometry.radiusPx * lerp(0.055f, 0.105f, foldT)).coerceAtLeast(8.dp.toPx())
    val perspectiveY = lerp(0.96f, 0.78f, foldT)
    val lift = geometry.radiusPx * 0.025f * foldT * (if (drilling) 1f else -1f)
    val rimTopLeft = Offset(geometry.center.x - radius, geometry.center.y - radius * perspectiveY + lift)
    val rimSize = Size(radius * 2f, radius * 2f * perspectiveY)

    drawCircle(
        color = Color.Black.copy(alpha = 0.16f * foldT),
        radius = radius + foldWidth * 0.38f,
        center = geometry.center.copy(y = geometry.center.y + foldWidth * 0.22f),
        style = Stroke(width = foldWidth * 1.25f),
    )

    drawOval(
        color = color.copy(alpha = 0.34f * foldT),
        topLeft = rimTopLeft,
        size = rimSize,
        style = Stroke(width = foldWidth),
    )
    drawOval(
        color = Color.White.copy(alpha = 0.18f * foldT),
        topLeft = rimTopLeft,
        size = rimSize,
        style = Stroke(width = foldWidth * 0.52f),
    )

    drawArc(
        color = Color.White.copy(alpha = 0.36f * foldT),
        startAngle = 205f,
        sweepAngle = 130f,
        useCenter = false,
        topLeft = rimTopLeft,
        size = rimSize,
        style = Stroke(width = foldWidth * 0.28f, cap = StrokeCap.Round),
    )
    drawArc(
        color = Color.Black.copy(alpha = 0.2f * foldT),
        startAngle = 18f,
        sweepAngle = 144f,
        useCenter = false,
        topLeft = rimTopLeft,
        size = rimSize,
        style = Stroke(width = foldWidth * 0.34f, cap = StrokeCap.Round),
    )
    drawCircle(
        color = color.copy(alpha = 0.62f * foldT),
        radius = radius,
        center = geometry.center,
        style = Stroke(width = 2.dp.toPx()),
    )
}

private fun lerp(
    start: Float,
    stop: Float,
    fraction: Float,
): Float = start + (stop - start) * fraction

private fun lerp(
    start: Offset,
    stop: Offset,
    fraction: Float,
): Offset =
    Offset(
        x = lerp(start.x, stop.x, fraction),
        y = lerp(start.y, stop.y, fraction),
    )

private fun eased(fraction: Float): Float = FastOutSlowInEasing.transform(fraction)

private fun rollProgress(
    started: Boolean,
    value: Float,
): Float = if (started) value else 0f
