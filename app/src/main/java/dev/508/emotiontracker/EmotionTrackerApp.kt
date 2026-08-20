package dev.508.emotiontracker

import android.app.Application
import dev.508.emotiontracker.data.EmotionRepository
import dev.508.emotiontracker.data.EmotionTreeLoader
import dev.508.emotiontracker.data.db.AppDatabase

/**
 * Hand-rolled composition root. No DI framework: the app is three screens
 * sharing one repository, so a framework would add ceremony without buying
 * much. Revisit if the module graph actually grows.
 */
class EmotionTrackerApp : Application() {
    lateinit var repository: EmotionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val tree = EmotionTreeLoader.load(this)
        val dao = AppDatabase.get(this).emotionEntryDao()
        repository = EmotionRepository(tree, dao)
    }
}
