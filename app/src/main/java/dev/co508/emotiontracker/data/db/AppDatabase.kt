package dev.co508.emotiontracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [EmotionEntryEntity::class, ReminderEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun emotionEntryDao(): EmotionEntryDao

    abstract fun reminderDao(): ReminderDao

    companion object {
        private const val DB_NAME = "emotion-tracker.db"

        /** Adds the `reminders` table (v1 shipped without any reminders feature). */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `reminders` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`hour` INTEGER NOT NULL, " +
                            "`minute` INTEGER NOT NULL, " +
                            "`enabled` INTEGER NOT NULL)",
                    )
                }
            }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        DB_NAME,
                    ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
