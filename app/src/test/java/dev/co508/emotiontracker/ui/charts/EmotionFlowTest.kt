package dev.co508.emotiontracker.ui.charts

import dev.co508.emotiontracker.data.EmotionNode
import dev.co508.emotiontracker.data.EmotionTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionFlowTest {
    private val content = node("content")
    private val relaxed = node("relaxed")
    private val calm = node("calm", content, relaxed)
    private val positive = node("positive", calm)
    private val sad = node("sad")
    private val negative = node("negative", sad)
    private val tree = EmotionTree(1, node("root", positive, negative))

    @Test
    fun `paths count each saved entry once at each ancestor and retain intermediate saves`() {
        val flow = buildEmotionFlow(tree, listOf("content", "content", "relaxed", "calm", "positive", "sad"))
        assertEquals(6, flow.entryCount)
        assertEquals(0, flow.unmappedCount)
        assertEquals(
            listOf("positive", "calm", "content", "relaxed", "negative", "sad"),
            flow.nodes.map { it.emotion.id },
        )
        assertEquals(listOf(5, 4, 2, 1, 1, 1), flow.nodes.map { it.count })
        assertEquals(listOf(1, 1, 2, 1, 0, 1), flow.nodes.map { it.savedHere })
        assertEquals(listOf(0, 1, 2, 2, 0, 1), flow.nodes.map { it.depth })
        flow.nodes.forEach { node ->
            assertEquals(
                node.count,
                node.savedHere + flow.links.filter { it.sourceId == node.emotion.id }.sumOf { it.count },
            )
        }
    }

    @Test
    fun `empty journal and unknown ids never create invented paths`() {
        assertTrue(buildEmotionFlow(tree, emptyList()).nodes.isEmpty())
        val flow = buildEmotionFlow(tree, listOf("removed", "root", "content"))
        assertEquals(1, flow.entryCount)
        assertEquals(2, flow.unmappedCount)
        assertFalse(flow.nodes.any { it.emotion.id == "negative" })
        assertEquals(0f, layoutEmotionFlow(buildEmotionFlow(tree, emptyList())).height)
    }

    @Test
    fun `reparented opaque id follows current tree ancestry`() {
        val changed = EmotionTree(2, node("root", node("positive"), node("negative", content)))
        val flow = buildEmotionFlow(changed, listOf("content"))
        assertEquals(listOf("negative", "content"), flow.nodes.map { it.emotion.id })
        assertEquals(listOf(EmotionFlowLink("negative", "content", 1)), flow.links)
    }

    @Test
    fun `saving only a top level emotion needs no links`() {
        val flow = buildEmotionFlow(tree, listOf("positive", "positive"))
        assertEquals(2, flow.nodes.single().savedHere)
        assertTrue(flow.links.isEmpty())
        assertEquals(400f, layoutEmotionFlow(flow).nodes.single().height)
    }

    @Test
    fun `layout preserves ribbon proportions and keeps labels and bands in bounds`() {
        val flow = buildEmotionFlow(tree, List(1000) { "content" } + listOf("relaxed", "calm", "sad"))
        val layout = layoutEmotionFlow(flow, labelHeight = 96f)
        val byId = layout.nodes.associateBy { it.node.emotion.id }
        layout.links.forEach { band ->
            val source = byId.getValue(band.link.sourceId)
            val target = byId.getValue(band.link.targetId)
            assertTrue(band.sourceY >= source.y)
            assertTrue(band.sourceY + band.height <= source.y + source.height + 0.001f)
            assertEquals(target.y, band.targetY, 0.001f)
            assertEquals(target.height, band.height, 0.001f)
            assertEquals(400f / flow.entryCount, band.height / band.link.count, 0.001f)
        }
        layout.nodes.groupBy { it.node.depth }.values.forEach { column ->
            column.zipWithNext().forEach { (first, second) ->
                assertTrue(first.labelY + 96f <= second.labelY)
                assertTrue(first.y + first.height <= second.y)
            }
        }
        layout.nodes.forEach {
            assertTrue(it.y >= 0f && it.y + it.height <= layout.height)
            assertTrue(it.labelY >= 0f && it.labelY + 96f <= layout.height)
            assertTrue(it.x + 192f <= layout.width)
        }
    }

    private fun node(
        id: String,
        vararg children: EmotionNode,
    ) = EmotionNode(id, id, "#123456", children.toList())
}
