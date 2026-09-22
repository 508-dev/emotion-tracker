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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
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

// With the soft-depth/glow treatment, wedges sit over a dark background at
// well under full opacity, so white reads better than the old dark/matte
// label color (which assumed a fully opaque, light pastel fill).
private val LabelColor = Color(0xFFF5F3F0)

private const val WIPE_DURATION_MS = 360

/**
 * How far past the wheel's own radius the outer glow reaches (as a
 * multiple of radiusPx). The Canvas reserves exactly this much margin (see
 * the radiusPx calculation in [EmotionWheel]) so the glow fully fades out
 * before hitting the Canvas edge — past which drawing is clipped — instead
 * of being cut off in a visible square. EmotionWheelScreen sizes the
 * wheel's incoming modifier generously enough that reserving this margin
 * doesn't noticeably shrink the interactive wheel itself.
 */
private const val GLOW_MARGIN_FACTOR = 1.35f

/**
 * Alpha a wedge's fill reaches at its own outer (rim) edge. Also the outer
 * glow's starting alpha at that same radius, so the fill hands off to the
 * glow at a matching brightness instead of visibly stepping down.
 */
private const val WEDGE_RIM_ALPHA = 0.85f

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
        val canvasSizePx = with(density) { minDp(maxWidth, maxHeight).toPx() }
        val canvasCenterPx = canvasSizePx / 2f
        // The Canvas draws nothing beyond its own layout bounds, so the wheel
        // itself is sized a bit smaller than the full canvas — leaving a
        // margin all the way around for the outer glow to fade out in,
        // instead of getting clipped square at the canvas edge.
        val radiusPx = canvasCenterPx / GLOW_MARGIN_FACTOR
        val geometry =
            WheelGeometry(
                center = Offset(canvasCenterPx, canvasCenterPx),
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
            // Draw outer glow for each wedge, using its emotion color
            children.forEachIndexed { i, child ->
                val startAngle = -90f + i * wedgeAngle
                drawWedgeGlow(
                    color = child.parsedColor,
                    startAngle = startAngle,
                    wedgeAngle = wedgeAngle,
                    center = center,
                    wheelRadius = radiusPx,
                )
            }

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
                val wedgePath = annularWedgePath(center, ringInnerRadiusPx, outerRadius, startAngle, wedgeAngle)

                // Draw wedge with soft glowing edges using depth/glow pass
                drawWedgeWithGlowingEdges(
                    path = wedgePath,
                    color = child.parsedColor,
                    startAngle = startAngle,
                    wedgeAngle = wedgeAngle,
                    innerRadius = ringInnerRadiusPx,
                    outerRadius = outerRadius,
                    center = center,
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
            val xPx = center.x + labelRadiusPx * cos(midAngleRad).toFloat()
            val yPx = center.y + labelRadiusPx * sin(midAngleRad).toFloat()
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

/**
 * Draws the outer glow for a wedge, using the wedge's color, as a true radial
 * gradient (not stacked flat-alpha shapes) so it fades continuously from the
 * wheel edge out to nothing, with no visible banding. Also eased angularly
 * (same per-wedge quadratic falloff as the fill) so neighboring glows blend
 * into each other instead of meeting at a hard color seam.
 */
private fun DrawScope.drawWedgeGlow(
    color: Color,
    startAngle: Float,
    wedgeAngle: Float,
    center: Offset,
    wheelRadius: Float,
) {
    val glowOuterRadius = wheelRadius * GLOW_MARGIN_FACTOR
    // Starts exactly at the wheel's own edge (not inset into it, which would
    // both double-draw over the fill and, worse, start the glow's decay from
    // a lower alpha than the fill's rim — the visible "step down" this
    // replaced). The innermost stop below matches WEDGE_RIM_ALPHA exactly so
    // the fill hands off to the glow at the same brightness it ends at.
    val glowInnerRadius = wheelRadius
    val glowPath = annularWedgePath(center, glowInnerRadius, glowOuterRadius, startAngle, wedgeAngle)
    val innerFrac = glowInnerRadius / glowOuterRadius

    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), Paint())
        drawPath(
            path = glowPath,
            brush =
                Brush.radialGradient(
                    // Many stops, front-loaded near the wheel edge, so the falloff
                    // reads as an exponential glow rather than a linear fade.
                    colorStops =
                        arrayOf(
                            innerFrac to color.copy(alpha = WEDGE_RIM_ALPHA),
                            (innerFrac + (1f - innerFrac) * 0.25f) to color.copy(alpha = 0.42f),
                            (innerFrac + (1f - innerFrac) * 0.5f) to color.copy(alpha = 0.2f),
                            (innerFrac + (1f - innerFrac) * 0.75f) to color.copy(alpha = 0.08f),
                            1f to Color.Transparent,
                        ),
                    center = center,
                    radius = glowOuterRadius,
                ),
        )
        drawAngularEasedMask(
            path = angularMaskPath(startAngle, wedgeAngle, glowInnerRadius, glowOuterRadius, center),
            startAngle = startAngle,
            wedgeAngle = wedgeAngle,
            center = center,
            lowAlpha = 0.6f,
            highAlpha = 1f,
        )
        canvas.restore()
    }
}

/**
 * Eases from [low] to [high] as [x] goes 0→1 along an x² curve: flat (near
 * [low]) through most of the range, with essentially all the change packed
 * into the last stretch before x=1. Used so a wedge's fill barely moves
 * through its interior and only brightens right at the true boundary.
 */
private fun easeQuadratic(
    low: Float,
    high: Float,
    x: Float,
) = low + (high - low) * x * x

/**
 * Alpha as a function of radius alone: dips to [lowAlpha] at the wedge's
 * radial midpoint and rises via [easeQuadratic] to [highAlpha] at both the
 * inner and outer radius. Sampled densely so the piecewise-linear gradient
 * stops read as a smooth curve rather than the crude 3-stop "V" this
 * replaced (which had a harsh, constant-rate linear transition throughout).
 */
private fun radialAlphaStops(
    innerRadius: Float,
    outerRadius: Float,
    lowAlpha: Float,
    highAlpha: Float,
    steps: Int = 24,
): List<Pair<Float, Float>> {
    val mid = (innerRadius + outerRadius) / 2f
    val halfSpan = (outerRadius - innerRadius) / 2f
    return (0..steps).map { i ->
        val r = innerRadius + (outerRadius - innerRadius) * (i / steps.toFloat())
        val xFromMid = (kotlin.math.abs(r - mid) / halfSpan).coerceIn(0f, 1f)
        (r / outerRadius) to easeQuadratic(lowAlpha, highAlpha, xFromMid)
    }
}

/**
 * Alpha as a function of angle alone, built in a *canonical* frame centered
 * on 180° rather than the wedge's true position. A [Brush.sweepGradient]
 * always starts its 0↔1 seam at 0° (3 o'clock); a wedge whose true span
 * straddles that direction would need offsets that wrap past 1.0, which
 * sweepGradient can't represent. Centering every wedge's stops at 180°
 * instead keeps them safely away from the seam (as long as wedgeAngleDeg
 * < 360°) — [drawAngularEasedWedge] then rotates the canvas so this
 * canonical gradient lands on the wedge's real position.
 */
private fun canonicalAngularAlphaStops(
    wedgeAngleDeg: Float,
    lowAlpha: Float,
    highAlpha: Float,
    steps: Int = 16,
): List<Pair<Float, Float>> {
    val startDeg = 180f - wedgeAngleDeg / 2f
    return (0..steps).map { i ->
        val u = i / steps.toFloat() // 0..1 across this wedge only
        val xFromMid = (kotlin.math.abs(u - 0.5f) * 2f).coerceIn(0f, 1f)
        ((startDeg + wedgeAngleDeg * u) / 360f) to easeQuadratic(highAlpha, lowAlpha, xFromMid)
    }
}

/**
 * Multiplies [color]'s alpha across [path] by a per-wedge angular falloff —
 * bright at the wedge's own angular center, soft where it meets its
 * neighbors — via a sweepGradient built in the seam-safe canonical frame
 * (see [canonicalAngularAlphaStops]) and rotated onto the wedge's true
 * position. Must be called with an isolated layer already active (e.g.
 * inside [drawIntoCanvas]'s `saveLayer`), since DstIn otherwise multiplies
 * against whatever the canvas already holds beneath it.
 */
private fun DrawScope.drawAngularEasedMask(
    path: Path,
    startAngle: Float,
    wedgeAngle: Float,
    center: Offset,
    lowAlpha: Float,
    highAlpha: Float,
) {
    val trueMid = startAngle + wedgeAngle / 2f
    val rotationDeg = trueMid - 180f
    val angularStops =
        canonicalAngularAlphaStops(wedgeAngle, lowAlpha, highAlpha)
            .map { (frac, alpha) -> frac to Color.White.copy(alpha = alpha) }
            .toTypedArray()
    rotate(degrees = rotationDeg, pivot = center) {
        drawPath(
            // Pre-rotated by -rotationDeg so it lands back on the wedge's
            // true position once the rotate() above is applied.
            path = path,
            brush = Brush.sweepGradient(colorStops = angularStops, center = center),
            blendMode = BlendMode.DstIn,
        )
    }
}

/**
 * Draws a wedge whose alpha is the product of two independent, per-wedge
 * quadratic-eased falloffs — one over radius (soft in the middle, bright at
 * the inner/outer edges) and one over angle (bright at the wedge's own
 * center, soft where it meets its neighbors). The two are combined by
 * rendering the radial gradient first, then multiplying in the angular one
 * via a DstIn blend inside an isolated layer, so neighboring wedges never
 * bleed into each other's math.
 */
private fun DrawScope.drawWedgeWithGlowingEdges(
    path: Path,
    color: Color,
    startAngle: Float,
    wedgeAngle: Float,
    innerRadius: Float,
    outerRadius: Float,
    center: Offset,
) {
    val radialStops =
        radialAlphaStops(innerRadius, outerRadius, lowAlpha = 0.48f, highAlpha = WEDGE_RIM_ALPHA)
            .map { (frac, alpha) -> frac to color.copy(alpha = alpha) }
            .toTypedArray()

    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), Paint())
        drawPath(
            path = path,
            brush = Brush.radialGradient(colorStops = radialStops, center = center, radius = outerRadius),
        )
        // The mask must be built for the wedge's true angular position, not
        // the canonical (rotated) one, since rotate() rotates this whole
        // path draw — so pass a path already re-expressed in the rotated
        // frame the same way angularAlphaMaskPath below expects.
        drawAngularEasedMask(
            path = angularMaskPath(startAngle, wedgeAngle, innerRadius, outerRadius, center),
            startAngle = startAngle,
            wedgeAngle = wedgeAngle,
            center = center,
            lowAlpha = 0.58f,
            highAlpha = 1f,
        )
        canvas.restore()
    }
}

/**
 * The same annular wedge shape as [annularWedgePath], but expressed in the
 * rotated frame [drawAngularEasedMask] draws in: shifted by -(trueMid -
 * 180°) so that after that rotation is applied, it lands back on the
 * wedge's true position.
 */
private fun angularMaskPath(
    startAngle: Float,
    wedgeAngle: Float,
    innerRadius: Float,
    outerRadius: Float,
    center: Offset,
): Path {
    val trueMid = startAngle + wedgeAngle / 2f
    val rotationDeg = trueMid - 180f
    return annularWedgePath(center, innerRadius, outerRadius, startAngle - rotationDeg, wedgeAngle)
}
