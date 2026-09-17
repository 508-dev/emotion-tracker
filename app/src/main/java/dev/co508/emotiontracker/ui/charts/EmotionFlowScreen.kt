package dev.co508.emotiontracker.ui.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.co508.emotiontracker.R

@Composable
fun EmotionFlowScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChartsViewModel = viewModel(factory = ChartsViewModel.Factory),
) {
    val flow by viewModel.emotionFlow.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Text(stringResource(R.string.chart_back), Modifier.padding(start = 8.dp))
        }
        Text(
            stringResource(R.string.chart_emotion_flow),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        val data = flow
        if (data == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.chart_loading))
            }
        } else {
            if (data.unmappedCount > 0) {
                Text(
                    stringResource(R.string.chart_unmapped, data.unmappedCount),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (data.nodes.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.chart_empty))
                }
            } else {
                Text(
                    stringResource(R.string.chart_entry_count, data.entryCount),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Text(
                    stringResource(R.string.chart_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Text(
                    stringResource(R.string.chart_scroll_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SankeyChart(
                    data,
                    onSelectNode = { selectedId = it.emotion.id },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            data.nodes.firstOrNull { it.emotion.id == selectedId }?.let { node ->
                AlertDialog(
                    onDismissRequest = { selectedId = null },
                    title = { Text(node.emotion.label) },
                    text = { Text(stringResource(R.string.chart_node_details, node.count, node.savedHere)) },
                    confirmButton = {
                        TextButton(onClick = { selectedId = null }) { Text(stringResource(R.string.chart_close)) }
                    },
                )
            }
        }
    }
}
