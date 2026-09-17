package dev.co508.emotiontracker.ui.charts

import dev.co508.emotiontracker.data.EmotionNode
import dev.co508.emotiontracker.data.EmotionTree

data class EmotionFlowNode(
    val emotion: EmotionNode,
    val depth: Int,
    val count: Int,
    val savedHere: Int,
)

data class EmotionFlowLink(
    val sourceId: String,
    val targetId: String,
    val count: Int,
)

data class EmotionFlow(
    val nodes: List<EmotionFlowNode>,
    val links: List<EmotionFlowLink>,
    val entryCount: Int,
    val unmappedCount: Int,
)

/**
 * Reconstructs saved paths using actual tree ancestry, never the spelling of an id.
 * Internal-node saves end there; removed ids are counted separately, not guessed.
 * Tree order keeps the branches stable and avoids crossing sibling flows.
 */
fun buildEmotionFlow(
    tree: EmotionTree,
    emotionIds: List<String>,
): EmotionFlow {
    val savedCounts = emotionIds.groupingBy { it }.eachCount()
    val nodes = mutableListOf<EmotionFlowNode>()
    val links = mutableListOf<EmotionFlowLink>()

    fun visit(
        emotion: EmotionNode,
        depth: Int,
    ): Int {
        val insertionIndex = nodes.size
        val savedHere = savedCounts[emotion.id] ?: 0
        val childCounts = emotion.children.map { it to visit(it, depth + 1) }
        val count = savedHere + childCounts.sumOf { it.second }
        if (count > 0) {
            nodes.add(insertionIndex, EmotionFlowNode(emotion, depth, count, savedHere))
            childCounts.filter { it.second > 0 }.forEach { (child, childCount) ->
                links.add(EmotionFlowLink(emotion.id, child.id, childCount))
            }
        }
        return count
    }

    val mappedCount = tree.root.children.sumOf { visit(it, 0) }
    return EmotionFlow(nodes, links, mappedCount, emotionIds.size - mappedCount)
}

data class FlowNodeBounds(
    val node: EmotionFlowNode,
    val x: Float,
    val y: Float,
    val height: Float,
    val labelY: Float,
)

data class FlowLinkBounds(
    val link: EmotionFlowLink,
    val sourceY: Float,
    val targetY: Float,
    val height: Float,
)

data class EmotionFlowLayout(
    val nodes: List<FlowNodeBounds>,
    val links: List<FlowLinkBounds>,
    val width: Float,
    val height: Float,
)

/** Geometry in logical units (dp at rendering time); every ribbon uses one shared count scale. */
fun layoutEmotionFlow(
    flow: EmotionFlow,
    labelHeight: Float = 48f,
): EmotionFlowLayout {
    if (flow.nodes.isEmpty()) return EmotionFlowLayout(emptyList(), emptyList(), 0f, 0f)
    val unit = 400f / flow.entryCount
    val gap = 24f
    val columns = flow.nodes.groupBy { it.depth }.toSortedMap()
    val heights =
        columns.mapValues { (_, nodes) ->
            nodes.sumOf { maxOf(it.count * unit, labelHeight).toDouble() }.toFloat() +
                gap * (nodes.size - 1)
        }
    val height = heights.values.max() + 32f
    val positions =
        columns.flatMap { (depth, nodes) ->
            var y = 16f
            nodes.map { node ->
                val bandHeight = node.count * unit
                val slotHeight = maxOf(bandHeight, labelHeight)
                FlowNodeBounds(
                    node,
                    16f + depth * 240f,
                    y + (slotHeight - bandHeight) / 2f,
                    bandHeight,
                    y + (slotHeight - labelHeight) / 2f,
                ).also {
                    y += slotHeight + gap
                }
            }
        }
    val byId = positions.associateBy { it.node.emotion.id }
    val used = mutableMapOf<String, Float>()
    val links =
        flow.links.map { link ->
            val source = byId.getValue(link.sourceId)
            val target = byId.getValue(link.targetId)
            val offset = used[link.sourceId] ?: 0f
            val bandHeight = link.count * unit
            used[link.sourceId] = offset + bandHeight
            FlowLinkBounds(link, source.y + offset, target.y, bandHeight)
        }
    return EmotionFlowLayout(positions, links, columns.size * 240f, height)
}
