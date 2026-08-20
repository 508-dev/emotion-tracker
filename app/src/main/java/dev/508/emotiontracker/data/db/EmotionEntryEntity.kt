package dev.508.emotiontracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One recorded emotion. Deliberately stores only the leaf [emotionId] from
 * the emotion tree, not the full path down to it — the tree can be
 * restructured (re-nested, relabeled, recolored) without invalidating
 * history. If an [emotionId] is later removed from the tree entirely, the
 * entry survives and falls back to its raw id for display; see
 * [dev.508.emotiontracker.data.EmotionRepository.resolve].
 */
@Entity(tableName = "emotion_entries")
data class EmotionEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val emotionId: String,
    val recordedAtEpochMillis: Long,
    val note: String? = null,
)
