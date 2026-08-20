package dev.508.emotiontracker.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.508.emotiontracker.ui.journal.JournalScreen
import dev.508.emotiontracker.ui.settings.SettingsScreen
import dev.508.emotiontracker.ui.wheel.EmotionWheelScreen

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = Destination.Wheel.route, modifier = modifier) {
        composable(Destination.Wheel.route) { EmotionWheelScreen(modifier = Modifier.fillMaxSize()) }
        composable(Destination.Journal.route) { JournalScreen(modifier = Modifier.fillMaxSize()) }
        composable(Destination.Settings.route) { SettingsScreen(modifier = Modifier.fillMaxSize()) }
    }
}
