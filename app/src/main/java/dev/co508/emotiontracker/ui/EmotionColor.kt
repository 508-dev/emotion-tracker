package dev.co508.emotiontracker.ui

import androidx.compose.ui.graphics.Color
import dev.co508.emotiontracker.data.EmotionNode

/** Parses the tree's `"#RRGGBB"` / `"#AARRGGBB"` hex strings. */
val EmotionNode.parsedColor: Color
    get() = Color(android.graphics.Color.parseColor(color))
