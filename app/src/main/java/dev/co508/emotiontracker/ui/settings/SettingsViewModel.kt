package dev.co508.emotiontracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.co508.emotiontracker.data.EmotionCsv
import dev.co508.emotiontracker.data.EmotionRepository
import dev.co508.emotiontracker.ui.repository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * "Delete all entries" requires, in order: a dismissible warning, then two
 * more taps of the same button (three taps total). Leaving the screen resets
 * the arm state — this view model is scoped to the composable's navigation
 * back stack entry, not to the app.
 */
sealed interface DeleteAllState {
    data object Idle : DeleteAllState

    data object WarningShown : DeleteAllState

    /** [tapsRemaining] more taps of the delete button will actually delete. */
    data class Armed(
        val tapsRemaining: Int,
    ) : DeleteAllState
}

class SettingsViewModel(
    private val repository: EmotionRepository,
) : ViewModel() {
    private val _deleteAllState = MutableStateFlow<DeleteAllState>(DeleteAllState.Idle)
    val deleteAllState: StateFlow<DeleteAllState> = _deleteAllState

    private val events = Channel<SettingsEvent>(Channel.BUFFERED)
    val eventFlow: Flow<SettingsEvent> = events.receiveAsFlow()

    /** The journal as CSV text — the caller (Settings screen) owns writing it to a user-chosen file. */
    suspend fun exportCsv(): String = EmotionCsv.export(repository.observeJournal().first(), repository.tree)

    /** Header-only CSV — the blank starting point for a hand-authored restore file. */
    fun templateCsv(): String = EmotionCsv.template()

    /** Parses [csv] and adds whatever resolves to the journal; never touches existing entries. */
    suspend fun restoreCsv(csv: String): RestoreOutcome {
        val result = EmotionCsv.parse(csv, repository.tree)
        repository.restoreEntries(result.entries)
        return RestoreOutcome(imported = result.entries.size, skipped = result.errors.size)
    }

    /** Call on every tap of the delete-all button; advances the confirmation state machine. */
    fun onDeleteAllClicked() {
        when (val state = _deleteAllState.value) {
            is DeleteAllState.Idle -> _deleteAllState.value = DeleteAllState.WarningShown
            is DeleteAllState.Armed -> {
                if (state.tapsRemaining <= 1) {
                    viewModelScope.launch {
                        repository.deleteAllEntries()
                        events.send(SettingsEvent.AllEntriesDeleted)
                        _deleteAllState.value = DeleteAllState.Idle
                    }
                } else {
                    _deleteAllState.value = DeleteAllState.Armed(state.tapsRemaining - 1)
                }
            }
            is DeleteAllState.WarningShown -> Unit // wait for explicit acknowledgement
        }
    }

    fun onWarningAcknowledged() {
        _deleteAllState.value = DeleteAllState.Armed(tapsRemaining = 2)
    }

    fun onWarningDismissed() {
        _deleteAllState.value = DeleteAllState.Idle
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer { SettingsViewModel(repository()) }
            }
    }
}

sealed interface SettingsEvent {
    data object AllEntriesDeleted : SettingsEvent
}

/** [imported] entries were added to the journal; [skipped] rows couldn't be resolved (bad date or unrecognized emotion). */
data class RestoreOutcome(
    val imported: Int,
    val skipped: Int,
)
