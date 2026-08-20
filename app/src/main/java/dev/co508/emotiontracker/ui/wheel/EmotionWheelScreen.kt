package dev.co508.emotiontracker.ui.wheel

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.co508.emotiontracker.R

@Composable
fun EmotionWheelScreen(
    modifier: Modifier = Modifier,
    viewModel: EmotionWheelViewModel = viewModel(factory = EmotionWheelViewModel.Factory),
) {
    val path by viewModel.path.collectAsState()
    val current = path.last()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.savedEmotionLabel.collect { label ->
            Toast.makeText(context, context.getString(R.string.wheel_saved_toast, label), Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = current.label,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Row {
                if (path.size > 1) {
                    TextButton(onClick = viewModel::back) {
                        Text(stringResource(R.string.wheel_back))
                    }
                }
                if (current.isLeaf) {
                    TextButton(onClick = viewModel::save) {
                        Text(stringResource(R.string.wheel_save))
                    }
                }
            }
        }

        EmotionWheel(
            path = path,
            onSelect = viewModel::select,
            onSave = viewModel::save,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
        )
    }
}
