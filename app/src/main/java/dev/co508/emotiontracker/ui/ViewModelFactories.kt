package dev.co508.emotiontracker.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import dev.co508.emotiontracker.EmotionTrackerApp
import dev.co508.emotiontracker.data.EmotionRepository

/** Fetches the app-scoped [EmotionRepository] inside a `viewModelFactory { initializer { ... } }` block. */
fun CreationExtras.repository(): EmotionRepository {
    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EmotionTrackerApp
    return app.repository
}
