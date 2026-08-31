package dev.co508.emotiontracker.ui.wheel

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.co508.emotiontracker.R
import dev.co508.emotiontracker.data.EmotionNode

@Composable
fun EmotionWheelScreen(
    modifier: Modifier = Modifier,
    onOpenJournal: () -> Unit = {},
    viewModel: EmotionWheelViewModel = viewModel(factory = EmotionWheelViewModel.Factory),
) {
    val path by viewModel.path.collectAsState()
    val root = path.first()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.savedEmotionLabel.collect { label ->
            Toast.makeText(context, context.getString(R.string.wheel_saved_toast, label), Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp)) {
            if (path.size > 1) {
                IconButton(onClick = viewModel::back) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.wheel_back))
                }
            }
        }

        // The prompt is the tree root's own label — fixed regardless of
        // depth. Progress through the tree shows up as breadcrumbs below,
        // not by replacing this text (see EmotionWheel for why: the current
        // level's name lives in the wheel's center hub instead).
        Text(
            text = root.label,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        Box(modifier = Modifier.fillMaxWidth().height(28.dp).padding(top = 8.dp, start = 16.dp, end = 16.dp)) {
            Breadcrumbs(
                nodes = path.drop(1),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            EmotionWheel(
                path = path,
                onSelect = viewModel::select,
                onSave = viewModel::save,
                modifier = Modifier.fillMaxWidth(0.82f),
            )
        }

        Text(
            text = stringResource(R.string.wheel_open_journal),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = TextDecoration.Underline,
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp, bottom = 20.dp)
                    .clickable(onClick = onOpenJournal),
        )
    }
}

/**
 * The path taken through the tree so far, most-recent last. The last crumb —
 * whatever the wheel's center hub would currently save — is underlined so
 * it's clear that's the level that gets recorded, not the full path.
 */
@Composable
private fun Breadcrumbs(
    nodes: List<EmotionNode>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
    ) {
        nodes.forEachIndexed { index, node ->
            if (index > 0) {
                Text(
                    text = " › ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                )
            }
            val isSaveLevel = index == nodes.lastIndex
            Text(
                text = node.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isSaveLevel) 0.9f else 0.55f),
                textDecoration = if (isSaveLevel) TextDecoration.Underline else null,
            )
        }
    }
}
