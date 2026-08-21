package dev.co508.emotiontracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.co508.emotiontracker.ui.components.AppScaffold
import dev.co508.emotiontracker.ui.theme.EmotionTrackerTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /**
     * Notification taps land here with [ACTION_OPEN_WHEEL]; each tap bumps this
     * counter so [AppScaffold] can reset navigation to the wheel even when the
     * activity was already alive on another screen.
     */
    private val openWheelRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOpenWheelIntent(intent)
        setContent {
            EmotionTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppScaffold(openWheelRequests = openWheelRequests)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenWheelIntent(intent)
    }

    private fun handleOpenWheelIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_WHEEL) {
            openWheelRequests.value += 1
        }
    }

    companion object {
        /** Action used by reminder notifications to open the app on the emotion wheel. */
        const val ACTION_OPEN_WHEEL = "dev.co508.emotiontracker.action.OPEN_WHEEL"
    }
}
