package dev.co508.emotiontracker.ui.wheel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

private const val WIPE_DURATION_MS = 360

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

/**
 * The emotion wheel: a center hub always names whatever level is currently
 * on screen (empty at the root, since there's nothing to save yet) and
 * tapping it saves that level. The current level's children — if any — fill
 * the rest of the disk as equal wedges reaching all the way to the outer
 * edge; tapping one drills one level deeper. There's no memory of ancestor
 * levels drawn as rings — that history lives in the breadcrumbs above the
 * wheel instead (see EmotionWheelScreen).
 *
 * Moving between levels plays a short "wipe" hinged on wherever the tap that
 * caused it was: the outgoing level peels back in a wedge centered on the
 * *opposite* side, shrinking from the full circle down to nothing, so the
 * new level underneath is revealed starting exactly at the tapped wedge and
 * finishing at the far side. Backing out (or saving, which can pop several
 * levels at once) reverses whichever wedge was originally tapped to get to
 * the level being left, so opening and closing read as mirror images.
 */
@Composable
fun EmotionWheel(
    path: List<EmotionNode>,
    onSelect: (EmotionNode) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    // Keep the outgoing level around for one wipe whenever the path changes.
    // (Writing state during composition is deliberate here: the writes
    // converge on the very next recomposition — the documented pattern for
    // "remember the previous value of an input".)
    var displayedPath by remember { mutableStateOf(path) }
    var outgoingPath by remember { mutableStateOf<List<EmotionNode>?>(null) }
    var transitionId by remember { mutableIntStateOf(0) }
    if (displayedPath != path) {
        outgoingPath = displayedPath
        displayedPath = path
        transitionId++
    }
    // Keyed to transitionId (not just remembered once) so a brand-new
    // Animatable already reads 0f on the very same frame outgoingPath turns
    // non-null. A shared Animatable reset via wipe.snapTo() inside the
    // LaunchedEffect below arrives a frame late — long enough for the old
    // level to flash fully hidden before the reset lands.
    val wipe = remember(transitionId) { Animatable(0f) }
    LaunchedEffect(transitionId) {
        if (outgoingPath != null) {
            // No finally/cleanup on cancellation: if a newer path arrives
            // mid-wipe, the newer composition has already replaced
            // outgoingPath and owns the rest of the animation.
            wipe.animateTo(1f, tween(WIPE_DURATION_MS, easing = EaseInOutCubic))
            outgoingPath = null
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
            // The real, current level always sits at the bottom, fully drawn
            // and interactive — an outgoing level (if any) only ever peels
            // back to reveal it, never the other way around.
            WheelLevel(
                current = current,
                canSave = canSave,
                pressedZone = pressedZone,
                pressProgress = pressProgress,
                geometry = geometry,
                modifier = Modifier.fillMaxSize(),
            )

            val outgoing = outgoingPath
            if (outgoing != null) {
                val drilling = displayedPath.size > outgoing.size
                // Drilling in: hinge on the wedge that was just tapped.
                // Backing out (or saving, which can pop several levels at
                // once): hinge on whichever wedge originally led to the
                // level now being left, so closing mirrors how it opened.
                val hingeAngleDeg =
                    (
                        if (drilling) {
                            wedgeCenterAngleDeg(displayedPath, displayedPath.lastIndex)
                        } else {
                            wedgeCenterAngleDeg(outgoing, displayedPath.size)
                        }
                    ) ?: -90f
                val halfWidthDeg = 180f * (1f - wipe.value)

                WheelLevel(
                    current = outgoing.last(),
                    canSave = outgoing.size > 1,
                    pressedZone = null,
                    pressProgress = pressProgress,
                    geometry = geometry,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                shape = WedgeClipShape(hingeAngleDeg + 180f, halfWidthDeg, geometry.radiusPx)
                                clip = true
                            },
                )
                Canvas(Modifier.fillMaxSize()) {
                    drawWipeEdges(geometry, hingeAngleDeg, halfWidthDeg)
                }
            }

            // Hit testing always uses the settled geometry of the displayed
            // level, so taps land even mid-wipe.
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
 * standalone when settled, and twice (outgoing + incoming) while a wipe
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
 * The angle (in the wheel's wedge convention: -90° = 12 o'clock, clockwise)
 * of the wedge that leads from [path]'s node at `index - 1` to the one at
 * [index] — i.e. whichever wedge was tapped to move between them. Null if
 * [index] isn't a reachable step (e.g. index 0, the root, has no such wedge).
 */
private fun wedgeCenterAngleDeg(
    path: List<EmotionNode>,
    index: Int,
): Float? {
    if (index <= 0 || index >= path.size) return null
    val parent = path[index - 1]
    if (parent.children.isEmpty()) return null
    val childIndex = parent.children.indexOfFirst { it.id == path[index].id }
    if (childIndex < 0) return null
    val wedgeAngle = 360f / parent.children.size
    return -90f + (childIndex + 0.5f) * wedgeAngle
}

/**
 * A pie slice centered on [centerAngleDeg], spanning ±[halfWidthDeg], used to
 * clip the outgoing level during the tap-point wipe: the clip's edges *are*
 * the wipe's leading edges, shrinking from the full circle down to nothing
 * as the incoming level is revealed underneath.
 */
private data class WedgeClipShape(
    private val centerAngleDeg: Float,
    private val halfWidthDeg: Float,
    private val radiusPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val sweep = (halfWidthDeg * 2f).coerceIn(0f, 360f)
        val path = Path()
        if (sweep > 0f) {
            val center = size.center
            if (sweep >= 359.9f) {
                path.addOval(Rect(center = center, radius = radiusPx))
            } else {
                path.moveTo(center.x, center.y)
                path.arcTo(
                    Rect(center = center, radius = radiusPx),
                    centerAngleDeg - halfWidthDeg,
                    sweep,
                    forceMoveTo = false,
                )
                path.close()
            }
        }
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

/** Soft shadow lines at the wipe's two current leading edges, full-radius (hub through ring). */
private fun DrawScope.drawWipeEdges(
    geometry: WheelGeometry,
    hingeAngleDeg: Float,
    halfWidthDeg: Float,
) {
    val oldCenterAngleDeg = hingeAngleDeg + 180f
    drawSoftDivider(geometry.center, 0f, geometry.radiusPx, oldCenterAngleDeg - halfWidthDeg)
    drawSoftDivider(geometry.center, 0f, geometry.radiusPx, oldCenterAngleDeg + halfWidthDeg)
}
