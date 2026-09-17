package dev.co508.emotiontracker.ui.charts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.co508.emotiontracker.R

/** Chart catalog: new charts get their own destination and an entry here. */
@Composable
fun ChartsScreen(
    onOpenEmotionFlow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(stringResource(R.string.nav_charts), style = MaterialTheme.typography.headlineMedium)
        Card(onClick = onOpenEmotionFlow, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.chart_emotion_flow), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.chart_emotion_flow_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
