package dev.co508.emotiontracker.data

import kotlinx.serialization.Serializable

/**
 * One node of the emotion wheel. The tree is dev-editable content: see
 * `app/src/main/assets/emotion_tree.json`. Nothing outside this file (and
 * the JSON) should need to change to add, rename, recolor, or re-nest
 * emotions.
 *
 * [id] is the stable identity recorded on journal entries — see
 * [dev.co508.emotiontracker.data.db.EmotionEntryEntity]. Renaming a [label] or
 * [color] is safe at any time; changing or removing an [id] orphans any
 * journal entries that already reference it (they fall back to showing the
 * raw id, see [EmotionRepository.resolve]).
 */
@Serializable
data class EmotionNode(
    val id: String,
    val label: String,
    val color: String,
    val children: List<EmotionNode> = emptyList(),
) {
    val isLeaf: Boolean get() = children.isEmpty()
}

@Serializable
data class EmotionTree(
    val version: Int,
    val root: EmotionNode,
)
