package dev.co508.emotiontracker.ui.wheel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.co508.emotiontracker.data.EmotionNode
import dev.co508.emotiontracker.data.EmotionRepository
import dev.co508.emotiontracker.ui.repository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SavedEmotion(
    val entryId: Long,
    val label: String,
)

class EmotionWheelViewModel(
    private val repository: EmotionRepository,
) : ViewModel() {
    private val root = repository.tree.root

    /** Path from the tree root down to the level currently on screen. Always non-empty. */
    private val _path = MutableStateFlow(listOf(root))
    val path: StateFlow<List<EmotionNode>> = _path

    private val savedEvents = Channel<SavedEmotion>(Channel.CONFLATED)

    /** Identifies the exact journal entry offered by the temporary Add note action. */
    val savedEmotions: Flow<SavedEmotion> = savedEvents.receiveAsFlow()

    fun select(child: EmotionNode) {
        _path.update { it + child }
    }

    fun back() {
        _path.update { if (it.size > 1) it.dropLast(1) else it }
    }

    /**
     * Records whatever level is currently on screen — not just a bottomed-out
     * leaf. Someone might only want to record "Positive" or "Calm" without
     * picking further, so the wheel's center hub offers save at any depth
     * past the root (see [EmotionWheel]).
     */
    fun save() {
        val current = _path.value.last()
        if (current == root) return
        viewModelScope.launch {
            val entryId = repository.recordEmotion(current.id)
            _path.value = listOf(root)
            savedEvents.send(SavedEmotion(entryId, current.label))
        }
    }

    fun updateNote(
        entryId: Long,
        note: String?,
    ) {
        viewModelScope.launch { repository.updateNote(entryId, note) }
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer { EmotionWheelViewModel(repository()) }
            }
    }
}
