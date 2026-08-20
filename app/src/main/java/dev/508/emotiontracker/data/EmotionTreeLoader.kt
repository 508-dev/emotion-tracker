package dev.508.emotiontracker.data

import android.content.Context
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/**
 * Loads the emotion wheel from the bundled dev-editable JSON asset. Called
 * once at app startup; the parsed tree is small and held in memory for the
 * process lifetime by [EmotionRepository].
 */
object EmotionTreeLoader {
    private const val ASSET_PATH = "emotion_tree.json"

    fun load(context: Context): EmotionTree {
        val raw = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        return json.decodeFromString(EmotionTree.serializer(), raw)
    }
}

/** Flattens a tree into an id-keyed lookup, built once and reused. */
fun EmotionTree.flattenById(): Map<String, EmotionNode> {
    val out = LinkedHashMap<String, EmotionNode>()
    fun visit(node: EmotionNode) {
        out[node.id] = node
        node.children.forEach(::visit)
    }
    visit(root)
    return out
}
