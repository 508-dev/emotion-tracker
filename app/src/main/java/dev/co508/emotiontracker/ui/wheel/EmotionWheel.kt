package dev.co508.emotiontracker.ui.wheel

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.co508.emotiontracker.R
import dev.co508.emotiontracker.data.EmotionNode
import dev.co508.emotiontracker.ui.parsedColor
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import androidx.compose.ui.unit.min as minDp

/**
 * The emotion wheel: [path]'s already-selected levels stack as concentric
 * colored rings from the outside in, and the current level's children fill
 * the remaining inner disk as equal wedges. When the current level has no
 * children, the inner disk becomes a single tappable "Save" circle.
 */
@Composable
fun EmotionWheel(
    path: List<EmotionNode>,
    onSelect: (EmotionNode) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = path.last()
    val ringNodes = path.drop(1)
    val children = current.children

    // Restarts from 0 each time the current level changes, so the newly
    // revealed ring/disk scales in rather than snapping into place.
    val entry = remember(current.id) { Animatable(0f) }
    LaunchedEffect(current.id) { entry.animateTo(1f, tween(220)) }
    val entryProgress = entry.value

    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val density = LocalDensity.current
        val sizePx = with(density) { minDp(maxWidth, maxHeight).toPx() }
        val radiusPx = sizePx / 2f
        val center = Offset(radiusPx, radiusPx)

        val minInnerRadiusPx = radiusPx * 0.30f
        val ringCount = ringNodes.size
        val ringThicknessPx =
            if (ringCount > 0) {
                min((radiusPx - minInnerRadiusPx) / ringCount, radiusPx * 0.18f)
            } else {
                0f
            }
        val innerRadiusPx = radiusPx - ringCount * ringThicknessPx
        val wedgeAngle = if (children.isNotEmpty()) 360f / children.size else 0f

        Canvas(modifier = Modifier.fillMaxSize()) {
            ringNodes.forEachIndexed { index, node ->
                // Only the newest (innermost) ring animates in; older rings
                // are already fully settled.
                val isNewest = index == ringNodes.lastIndex
                val thickness = if (isNewest) ringThicknessPx * entryProgress else ringThicknessPx
                val midRadius = radiusPx - index * ringThicknessPx - thickness / 2f
                drawArc(
                    color = node.parsedColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = thickness.coerceAtLeast(1f)),
                    topLeft = Offset(center.x - midRadius, center.y - midRadius),
                    size = Size(midRadius * 2, midRadius * 2),
                )
            }

            if (current.isLeaf) {
                drawCircle(color = current.parsedColor, radius = innerRadiusPx, center = center, alpha = entryProgress)
            } else {
                children.forEachIndexed { i, child ->
                    val startAngle = -90f + i * wedgeAngle
                    drawArc(
                        color = child.parsedColor,
                        startAngle = startAngle,
                        sweepAngle = wedgeAngle,
                        useCenter = true,
                        topLeft = Offset(center.x - innerRadiusPx, center.y - innerRadiusPx),
                        size = Size(innerRadiusPx * 2, innerRadiusPx * 2),
                        alpha = entryProgress,
                    )
                }
                if (children.size > 1) {
                    children.indices.forEach { i ->
                        val edgeAngleRad = Math.toRadians((-90f + i * wedgeAngle).toDouble())
                        drawLine(
                            color = Color.Black.copy(alpha = 0.12f * entryProgress),
                            start = center,
                            end =
                                Offset(
                                    center.x + innerRadiusPx * cos(edgeAngleRad).toFloat(),
                                    center.y + innerRadiusPx * sin(edgeAngleRad).toFloat(),
                                ),
                            strokeWidth = with(density) { 1.dp.toPx() },
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(current.id) {
                    detectTapGestures { offset ->
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        if (hypot(dx, dy) > innerRadiusPx) return@detectTapGestures

                        if (current.isLeaf) {
                            onSave()
                            return@detectTapGestures
                        }
                        if (children.isEmpty()) return@detectTapGestures

                        val angleDeg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                        val relativeDeg = ((angleDeg + 90f) % 360f + 360f) % 360f
                        val index = (relativeDeg / wedgeAngle).toInt().coerceIn(0, children.size - 1)
                        onSelect(children[index])
                    }
                },
        )

        if (current.isLeaf) {
            Text(
                text = stringResource(R.string.wheel_save),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            val labelWidthDp = 84.dp
            val labelWidthPx = with(density) { labelWidthDp.toPx() }
            children.forEachIndexed { i, child ->
                val midAngleDeg = -90f + (i + 0.5f) * wedgeAngle
                val midAngleRad = Math.toRadians(midAngleDeg.toDouble())
                val labelRadiusPx = innerRadiusPx * 0.62f
                val xPx = radiusPx + labelRadiusPx * cos(midAngleRad).toFloat()
                val yPx = radiusPx + labelRadiusPx * sin(midAngleRad).toFloat()
                val offsetX = with(density) { (xPx - labelWidthPx / 2f).toDp() }
                val offsetY = with(density) { (yPx - 10.dp.toPx()).toDp() }

                Box(modifier = Modifier.offset(x = offsetX, y = offsetY).width(labelWidthDp)) {
                    Text(
                        text = child.label,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.width(labelWidthDp),
                    )
                }
            }
        }
    }
}
