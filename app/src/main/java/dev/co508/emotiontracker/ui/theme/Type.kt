package dev.co508.emotiontracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Material3's default type scale, with a bolder headline for the wheel's
// current-level title (see ui/wheel/EmotionWheelScreen.kt).
val AppTypography =
    Typography(
        headlineMedium =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
            ),
    )
