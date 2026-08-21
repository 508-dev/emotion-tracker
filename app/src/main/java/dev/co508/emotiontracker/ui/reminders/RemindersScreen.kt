package dev.co508.emotiontracker.ui.reminders

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.co508.emotiontracker.R
import dev.co508.emotiontracker.data.Reminder
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the time picker was opened for; null means no picker is showing. */
private sealed interface TimePickerRequest {
    data object Add : TimePickerRequest

    data class Edit(
        val id: Long,
        val hour: Int,
        val minute: Int,
    ) : TimePickerRequest
}

/** A user action deferred until notification permission is granted. */
private sealed interface PendingPermissionAction {
    data class AddReminder(
        val hour: Int,
        val minute: Int,
    ) : PendingPermissionAction

    data class EnableReminder(
        val id: Long,
    ) : PendingPermissionAction
}

@Composable
fun RemindersScreen(
    modifier: Modifier = Modifier,
    viewModel: RemindersViewModel = viewModel(factory = RemindersViewModel.Factory),
) {
    val reminders by viewModel.reminders.collectAsState()
    val context = LocalContext.current
    var timePicker by remember { mutableStateOf<TimePickerRequest?>(null) }
    var pendingPermissionAction by remember { mutableStateOf<PendingPermissionAction?>(null) }
    var showExactAlarmPrompt by remember { mutableStateOf(false) }

    val exactAlarmSettingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // The user may have just granted exact-alarm access — re-arm so it
            // takes effect immediately rather than after the next fire.
            viewModel.resync()
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            when (val action = pendingPermissionAction) {
                is PendingPermissionAction.AddReminder ->
                    if (granted) {
                        viewModel.addReminder(action.hour, action.minute)
                        if (!canScheduleExactAlarms(context)) showExactAlarmPrompt = true
                    } else {
                        Toast.makeText(context, R.string.reminders_permission_denied_toast, Toast.LENGTH_LONG).show()
                    }
                is PendingPermissionAction.EnableReminder ->
                    if (granted) {
                        viewModel.setEnabled(action.id, true)
                        if (!canScheduleExactAlarms(context)) showExactAlarmPrompt = true
                    } else {
                        Toast.makeText(context, R.string.reminders_permission_denied_toast, Toast.LENGTH_LONG).show()
                    }
                null -> Unit
            }
            pendingPermissionAction = null
        }

    fun addOrRequestPermission(
        hour: Int,
        minute: Int,
    ) {
        if (hasNotificationPermission(context)) {
            viewModel.addReminder(hour, minute)
            if (!canScheduleExactAlarms(context)) showExactAlarmPrompt = true
        } else {
            pendingPermissionAction = PendingPermissionAction.AddReminder(hour, minute)
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun enableOrRequestPermission(reminder: Reminder) {
        if (hasNotificationPermission(context)) {
            viewModel.setEnabled(reminder.id, true)
            if (!canScheduleExactAlarms(context)) showExactAlarmPrompt = true
        } else {
            pendingPermissionAction = PendingPermissionAction.EnableReminder(reminder.id)
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (reminders.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
            ) {
                Text(stringResource(R.string.reminders_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.reminders_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(bottom = 88.dp)) {
                items(reminders, key = { it.id }) { reminder ->
                    ReminderRow(
                        reminder = reminder,
                        onTimeClick = {
                            timePicker =
                                TimePickerRequest.Edit(reminder.id, reminder.hour, reminder.minute)
                        },
                        onToggle = { enabled ->
                            if (enabled) {
                                enableOrRequestPermission(reminder)
                            } else {
                                viewModel.setEnabled(reminder.id, false)
                            }
                        },
                        onDelete = { viewModel.deleteReminder(reminder.id) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { timePicker = TimePickerRequest.Add },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.reminders_add))
        }
    }

    when (val request = timePicker) {
        is TimePickerRequest.Add ->
            ReminderTimePickerDialog(
                initialHour = 9,
                initialMinute = 0,
                onConfirm = { hour, minute ->
                    timePicker = null
                    addOrRequestPermission(hour, minute)
                },
                onDismiss = { timePicker = null },
            )
        is TimePickerRequest.Edit ->
            ReminderTimePickerDialog(
                initialHour = request.hour,
                initialMinute = request.minute,
                onConfirm = { hour, minute ->
                    timePicker = null
                    viewModel.updateReminderTime(request.id, hour, minute)
                },
                onDismiss = { timePicker = null },
            )
        null -> Unit
    }

    if (showExactAlarmPrompt) {
        AlertDialog(
            onDismissRequest = { showExactAlarmPrompt = false },
            title = { Text(stringResource(R.string.reminders_exact_alarm_title)) },
            text = { Text(stringResource(R.string.reminders_exact_alarm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExactAlarmPrompt = false
                        // Unreachable below API 31 (the prompt only shows when
                        // exact alarms are denied, which requires API 31+), but
                        // the constant itself is API 31+.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            exactAlarmSettingsLauncher.launch(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    "package:${context.packageName}".toUri(),
                                ),
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.reminders_exact_alarm_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExactAlarmPrompt = false }) {
                    Text(stringResource(R.string.reminders_time_cancel))
                }
            },
        )
    }
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    onTimeClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val time =
        remember(reminder.hour, reminder.minute) {
            LocalTime
                .of(reminder.hour, reminder.minute)
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        }
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onTimeClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(time, style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(if (reminder.enabled) R.string.reminders_daily else R.string.reminders_disabled),
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (reminder.enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.reminders_delete))
        }
        Switch(checked = reminder.enabled, onCheckedChange = onToggle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = is24Hour)
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(stringResource(R.string.reminders_time_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.reminders_time_cancel))
            }
        },
        title = { Text(stringResource(R.string.reminders_time_title)) },
    ) {
        TimePicker(state = state)
    }
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

private fun canScheduleExactAlarms(context: Context): Boolean {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
}
