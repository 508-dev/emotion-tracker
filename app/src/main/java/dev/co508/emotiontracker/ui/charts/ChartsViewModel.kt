package dev.co508.emotiontracker.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.co508.emotiontracker.data.EmotionRepository
import dev.co508.emotiontracker.ui.repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ChartsViewModel(
    repository: EmotionRepository,
) : ViewModel() {
    val emotionFlow: StateFlow<EmotionFlow?> =
        repository
            .observeJournal()
            .map { entries -> buildEmotionFlow(repository.tree, entries.map { it.emotionId }) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory =
            viewModelFactory {
                initializer { ChartsViewModel(repository()) }
            }
    }
}
