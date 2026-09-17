package dev.co508.emotiontracker.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Scrollable2DState
import androidx.compose.foundation.gestures.scrollable2D
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.co508.emotiontracker.R
import dev.co508.emotiontracker.ui.parsedColor

/** Scrollable native chart. Labels are Compose elements so counts also work with TalkBack. */
@Composable
fun SankeyChart(
    flow: EmotionFlow,
    onSelectNode: (EmotionFlowNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    val labelHeight = 56f * maxOf(fontScale, 1f)
    val layout = remember(flow, labelHeight) { layoutEmotionFlow(flow, labelHeight) }
    val byId = remember(layout) { layout.nodes.associateBy { it.node.emotion.id } }
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val layoutDirection = LocalLayoutDirection.current
    val pan =
        remember(horizontal, vertical, layoutDirection) {
            chartPanState(horizontal, vertical, layoutDirection)
        }

    // Keep the scroll modifiers for measurement, clipping, bounds and saved positions,
    // but give gestures to one 2D handler so neither axis can capture a diagonal drag.
    Box(
        modifier =
            modifier
                .scrollable2D(pan)
                .horizontalScroll(horizontal, enabled = false)
                .verticalScroll(vertical, enabled = false),
    ) {
        Box(Modifier.size(layout.width.dp, layout.height.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                layout.links.forEach { band ->
                    val source = byId.getValue(band.link.sourceId)
                    val target = byId.getValue(band.link.targetId)
                    val startX = (source.x + 14f).dp.toPx()
                    val endX = target.x.dp.toPx()
                    val middleX = (startX + endX) / 2f
                    val sourceY = band.sourceY.dp.toPx()
                    val targetY = band.targetY.dp.toPx()
                    val height = band.height.dp.toPx()
                    val path =
                        Path().apply {
                            moveTo(startX, sourceY)
                            cubicTo(middleX, sourceY, middleX, targetY, endX, targetY)
                            lineTo(endX, targetY + height)
                            cubicTo(middleX, targetY + height, middleX, sourceY + height, startX, sourceY + height)
                            close()
                        }
                    drawPath(
                        path,
                        Brush.horizontalGradient(
                            listOf(
                                source.node.emotion.parsedColor
                                    .copy(alpha = 0.55f),
                                target.node.emotion.parsedColor
                                    .copy(alpha = 0.55f),
                            ),
                            startX = startX,
                            endX = endX,
                        ),
                    )
                }
                layout.nodes.forEach { bounds ->
                    drawRect(
                        color = bounds.node.emotion.parsedColor,
                        topLeft = Offset(bounds.x.dp.toPx(), bounds.y.dp.toPx()),
                        size = Size(14.dp.toPx(), bounds.height.dp.toPx()),
                    )
                }
            }
            layout.nodes.forEach { bounds ->
                Box(
                    modifier =
                        Modifier
                            .offset((bounds.x + 18f).dp, bounds.labelY.dp)
                            .size(174.dp, labelHeight.dp)
                            .clickable(role = Role.Button) { onSelectNode(bounds.node) },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = stringResource(R.string.chart_node_label, bounds.node.emotion.label, bounds.node.count),
                        style = MaterialTheme.typography.labelLarge,
                        modifier =
                            Modifier
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                ).padding(4.dp),
                    )
                }
            }
        }
    }
}

internal fun chartPanState(
    horizontal: ScrollState,
    vertical: ScrollState,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
): Scrollable2DState {
    val horizontalSign = if (layoutDirection == LayoutDirection.Rtl) 1f else -1f
    return Scrollable2DState { delta ->
        Offset(
            horizontalSign * horizontal.dispatchRawDelta(horizontalSign * delta.x),
            -vertical.dispatchRawDelta(-delta.y),
        )
    }
}
