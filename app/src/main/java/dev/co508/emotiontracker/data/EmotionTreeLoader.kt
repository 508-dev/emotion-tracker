package dev.co508.emotiontracker.data

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
        val raw =
            context.assets
                .open(ASSET_PATH)
                .bufferedReader()
                .use { it.readText() }
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

/** Segments the level labels are joined with in a CSV path — see [labelPath] and [resolveLabelPath]. */
private const val PATH_SEPARATOR = " > "

/**
 * The chain of labels from the root down to [id], e.g. "Positive > Calm >
 * Content > Satisfied" — the same breadcrumb text the wheel shows, used so a
 * CSV export/template is readable and editable without knowing raw tree ids.
 * The root's own label (the wheel's prompt, not part of any emotion's
 * identity) is never included. Null if [id] isn't in the tree.
 */
fun EmotionTree.labelPath(id: String): String? {
    val labels = mutableListOf<String>()

    fun visit(node: EmotionNode): Boolean {
        if (node.id == id) return true
        for (child in node.children) {
            labels += child.label
            if (visit(child)) return true
            labels.removeAt(labels.size - 1)
        }
        return false
    }

    return if (visit(root)) labels.joinToString(PATH_SEPARATOR) else null
}

/**
 * The inverse of [labelPath]: resolves a " > "-joined chain of labels back to
 * a node id, matching each segment case-insensitively against that level's
 * children. Null if any segment doesn't match — e.g. a typo, or the tree was
 * restructured since the path was written down.
 */
fun EmotionTree.resolveLabelPath(path: String): String? {
    val segments = path.split(">").map { it.trim() }.filter { it.isNotEmpty() }
    if (segments.isEmpty()) return null

    var node = root
    for (segment in segments) {
        node = node.children.firstOrNull { it.label.equals(segment, ignoreCase = true) } ?: return null
    }
    return node.id
}
