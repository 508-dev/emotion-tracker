package dev.co508.emotiontracker.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import dev.co508.emotiontracker.R

/** The hamburger menu's destinations, in the order the spec lists them. */
enum class Destination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Wheel("wheel", R.string.nav_wheel, Icons.Filled.DonutLarge),
    Journal("journal", R.string.nav_journal, Icons.AutoMirrored.Filled.MenuBook),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings),
}
