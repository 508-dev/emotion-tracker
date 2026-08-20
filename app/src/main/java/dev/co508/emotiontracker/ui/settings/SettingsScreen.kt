package dev.co508.emotiontracker.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.co508.emotiontracker.R

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val deleteAllState by viewModel.deleteAllState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            val message =
                when (event) {
                    SettingsEvent.ExportNotImplemented -> context.getString(R.string.settings_export_csv_toast)
                    SettingsEvent.AllEntriesDeleted -> context.getString(R.string.settings_delete_all_done_toast)
                }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(stringResource(R.string.settings_data_section), style = MaterialTheme.typography.titleMedium)

        OutlinedButton(
            onClick = viewModel::onExportCsvClicked,
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_export_csv))
        }

        Button(
            onClick = viewModel::onDeleteAllClicked,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_delete_all))
        }

        val armedHint = (deleteAllState as? DeleteAllState.Armed)?.tapsRemaining
        if (armedHint != null) {
            Text(
                text =
                    stringResource(
                        if (armedHint >
                            1
                        ) {
                            R.string.settings_delete_all_confirm_2
                        } else {
                            R.string.settings_delete_all_confirm_1
                        },
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Text(
            stringResource(R.string.settings_about_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            stringResource(R.string.settings_about_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    if (deleteAllState is DeleteAllState.WarningShown) {
        AlertDialog(
            onDismissRequest = viewModel::onWarningDismissed,
            title = { Text(stringResource(R.string.settings_delete_all_warning_title)) },
            text = { Text(stringResource(R.string.settings_delete_all_warning_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::onWarningAcknowledged) {
                    Text(stringResource(R.string.settings_delete_all_warning_ack))
                }
            },
        )
    }
}
