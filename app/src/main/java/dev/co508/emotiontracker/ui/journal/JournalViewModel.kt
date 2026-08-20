package dev.co508.emotiontracker.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.co508.emotiontracker.data.EmotionRepository
import dev.co508.emotiontracker.data.JournalEntry
import dev.co508.emotiontracker.ui.repository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JournalViewModel(
    private val repository: EmotionRepository,
) : ViewModel() {
    val entries: StateFlow<List<JournalEntry>> =
        repository
            .observeJournal()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateNote(
        entryId: Long,
        note: String?,
    ) {
        viewModelScope.launch { repository.updateNote(entryId, note) }
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer { JournalViewModel(repository()) }
            }
    }
}
