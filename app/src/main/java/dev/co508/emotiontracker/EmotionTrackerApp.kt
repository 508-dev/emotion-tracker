package dev.co508.emotiontracker

import android.app.Application
import dev.co508.emotiontracker.data.EmotionRepository
import dev.co508.emotiontracker.data.EmotionTreeLoader
import dev.co508.emotiontracker.data.ReminderRepository
import dev.co508.emotiontracker.data.db.AppDatabase
import dev.co508.emotiontracker.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled composition root. No DI framework: the app is a few screens
 * sharing one repository, so a framework would add ceremony without buying
 * much. Revisit if the module graph actually grows.
 */
class EmotionTrackerApp : Application() {
    lateinit var repository: EmotionRepository
        private set

    lateinit var reminderRepository: ReminderRepository
        private set

    lateinit var reminderScheduler: ReminderScheduler
        private set

    /** Process-lifetime scope for app-level background work (e.g. the boot receiver). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val tree = EmotionTreeLoader.load(this)
        val db = AppDatabase.get(this)
        repository = EmotionRepository(tree, db.emotionEntryDao())
        reminderRepository = ReminderRepository(db.reminderDao())
        reminderScheduler = ReminderScheduler(this)
        reminderScheduler.ensureNotificationChannel()
    }
}
