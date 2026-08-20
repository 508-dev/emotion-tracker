package dev.508.emotiontracker.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.508.emotiontracker.R
import dev.508.emotiontracker.data.JournalEntry
import dev.508.emotiontracker.ui.parsedColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun JournalScreen(modifier: Modifier = Modifier, viewModel: JournalViewModel = viewModel(factory = JournalViewModel.Factory)) {
    val entries by viewModel.entries.collectAsState()
    var noteEditorFor by remember { mutableStateOf<JournalEntry?>(null) }

    if (entries.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.journal_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.journal_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
                )
            }
        }
        return
    }

    val zone = ZoneId.systemDefault()
    val grouped = entries.groupBy { Instant.ofEpochMilli(it.recordedAtEpochMillis).atZone(zone).toLocalDate() }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        grouped.forEach { (day, dayEntries) ->
            item(key = "header-$day") {
                Text(
                    text = day.label(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(dayEntries, key = { it.id }) { entry ->
                JournalEntryRow(
                    entry = entry,
                    onClick = { noteEditorFor = entry },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }

    noteEditorFor?.let { entry ->
        NoteEditorDialog(
            entry = entry,
            onDismiss = { noteEditorFor = null },
            onSave = { note ->
                viewModel.updateNote(entry.id, note)
                noteEditorFor = null
            },
        )
    }
}

private fun LocalDate.label(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
    }
}

@Composable
private fun JournalEntryRow(entry: JournalEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val time = remember(entry.recordedAtEpochMillis) {
        Instant.ofEpochMilli(entry.recordedAtEpochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }
    val label = entry.emotion?.label ?: entry.emotionId
    val color = entry.emotion?.parsedColor ?: Color.Gray

    Row(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(modifier = Modifier.padding(top = 4.dp).size(12.dp).clip(CircleShape).background(color))
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(time, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = entry.note ?: stringResource(R.string.journal_add_note),
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.note != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun NoteEditorDialog(entry: JournalEntry, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var text by remember(entry.id) { mutableStateOf(entry.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (entry.note == null) R.string.journal_add_note else R.string.journal_edit_note)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.journal_note_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.journal_note_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.journal_note_cancel)) }
        },
    )
}
