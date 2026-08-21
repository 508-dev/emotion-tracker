package dev.co508.emotiontracker.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import dev.co508.emotiontracker.EmotionTrackerApp
import dev.co508.emotiontracker.data.EmotionRepository
import dev.co508.emotiontracker.data.ReminderRepository
import dev.co508.emotiontracker.reminders.ReminderScheduler

/** Fetches the app-scoped [EmotionTrackerApp] inside a `viewModelFactory { initializer { ... } }` block. */
fun CreationExtras.app(): EmotionTrackerApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EmotionTrackerApp

fun CreationExtras.repository(): EmotionRepository = app().repository

fun CreationExtras.reminderRepository(): ReminderRepository = app().reminderRepository

fun CreationExtras.reminderScheduler(): ReminderScheduler = app().reminderScheduler
