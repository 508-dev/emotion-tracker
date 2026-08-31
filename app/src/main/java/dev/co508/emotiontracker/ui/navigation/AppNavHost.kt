package dev.co508.emotiontracker.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.co508.emotiontracker.ui.journal.JournalScreen
import dev.co508.emotiontracker.ui.reminders.RemindersScreen
import dev.co508.emotiontracker.ui.settings.SettingsScreen
import dev.co508.emotiontracker.ui.wheel.EmotionWheelScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = Destination.Wheel.route, modifier = modifier) {
        composable(Destination.Wheel.route) {
            EmotionWheelScreen(
                modifier = Modifier.fillMaxSize(),
                onOpenJournal = {
                    navController.navigate(Destination.Journal.route) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Destination.Journal.route) { JournalScreen(modifier = Modifier.fillMaxSize()) }
        composable(Destination.Reminders.route) { RemindersScreen(modifier = Modifier.fillMaxSize()) }
        composable(Destination.Settings.route) { SettingsScreen(modifier = Modifier.fillMaxSize()) }
    }
}
