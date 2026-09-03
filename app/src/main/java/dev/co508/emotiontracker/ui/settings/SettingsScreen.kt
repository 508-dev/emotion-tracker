package dev.co508.emotiontracker.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.co508.emotiontracker.R
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val deleteAllState by viewModel.deleteAllState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            val message =
                when (event) {
                    SettingsEvent.AllEntriesDeleted -> context.getString(R.string.settings_delete_all_done_toast)
                }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun writeCsv(
        uri: Uri?,
        doneMessageRes: Int,
        content: suspend () -> String,
    ) {
        if (uri == null) return
        scope.launch {
            val outcome =
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(content().toByteArray()) }
                        ?: error("no output stream for $uri")
                }
            val messageRes = if (outcome.isSuccess) doneMessageRes else R.string.settings_csv_failed_toast
            Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
        }
    }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            writeCsv(uri, R.string.settings_export_csv_done_toast, viewModel::exportCsv)
        }

    val templateLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            writeCsv(uri, R.string.settings_template_csv_done_toast) { viewModel.templateCsv() }
        }

    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val outcome =
                    runCatching {
                        val csv =
                            context.contentResolver
                                .openInputStream(uri)
                                ?.bufferedReader()
                                ?.use { it.readText() }
                                ?: error("no input stream for $uri")
                        viewModel.restoreCsv(csv)
                    }
                val message =
                    outcome.fold(
                        onSuccess = {
                            context.getString(
                                R.string.settings_restore_csv_done_toast,
                                it.imported,
                                it.skipped,
                            )
                        },
                        onFailure = { context.getString(R.string.settings_csv_failed_toast) },
                    )
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(stringResource(R.string.settings_data_section), style = MaterialTheme.typography.titleMedium)

        OutlinedButton(
            onClick = { exportLauncher.launch(csvFileName("emotion-tracker")) },
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_export_csv))
        }

        OutlinedButton(
            onClick = { templateLauncher.launch(csvFileName("emotion-tracker-template")) },
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_download_template_csv))
        }

        OutlinedButton(
            onClick = { restoreLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) },
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_restore_csv))
        }
        Text(
            stringResource(R.string.settings_restore_csv_hint),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )

        Button(
            onClick = viewModel::onDeleteAllClicked,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
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

private fun csvFileName(prefix: String): String = "$prefix-${LocalDate.now()}.csv"
