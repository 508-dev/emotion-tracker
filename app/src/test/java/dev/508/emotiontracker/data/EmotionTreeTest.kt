package dev.508.emotiontracker.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Loads the real, shipped `emotion_tree.json` straight off disk (no
 * AssetManager/Android context needed for a plain JVM unit test) so a
 * dev-editing mistake in the tree fails fast in `./gradlew test` instead of
 * only surfacing at runtime on a device.
 */
class EmotionTreeTest {
    private val tree: EmotionTree by lazy {
        val file = File("src/main/assets/emotion_tree.json")
        Json { ignoreUnknownKeys = true }.decodeFromString(EmotionTree.serializer(), file.readText())
    }

    @Test
    fun `every node id is unique`() {
        val ids = tree.flattenById().keys
        val idsInOrder = mutableListOf<String>()
        fun visit(node: EmotionNode) {
            idsInOrder += node.id
            node.children.forEach(::visit)
        }
        visit(tree.root)
        assertEquals("duplicate ids in emotion_tree.json", idsInOrder.size, ids.size)
    }

    @Test
    fun `every node has a non-blank label and a parseable hex color`() {
        fun visit(node: EmotionNode) {
            assertFalse("blank label for id=${node.id}", node.label.isBlank())
            assertTrue(
                "unparseable color '${node.color}' for id=${node.id}",
                node.color.matches(Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")),
            )
            node.children.forEach(::visit)
        }
        visit(tree.root)
    }

    @Test
    fun `isLeaf reflects an empty children list`() {
        val leaf = EmotionNode(id = "x", label = "X", color = "#000000")
        val branch = EmotionNode(id = "y", label = "Y", color = "#000000", children = listOf(leaf))

        assertTrue(leaf.isLeaf)
        assertFalse(branch.isLeaf)
    }

    @Test
    fun `flattenById finds every descendant, including leaves`() {
        val byId = tree.flattenById()
        assertTrue(byId.containsKey(tree.root.id))
        tree.root.children.forEach { child ->
            assertTrue(byId.containsKey(child.id))
            child.children.forEach { grandchild -> assertTrue(byId.containsKey(grandchild.id)) }
        }
    }
}
