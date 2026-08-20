package dev.508.emotiontracker.ui.wheel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.508.emotiontracker.data.EmotionNode
import dev.508.emotiontracker.data.EmotionRepository
import dev.508.emotiontracker.ui.repository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EmotionWheelViewModel(private val repository: EmotionRepository) : ViewModel() {
    private val root = repository.tree.root

    /** Path from the tree root down to the level currently on screen. Always non-empty. */
    private val _path = MutableStateFlow(listOf(root))
    val path: StateFlow<List<EmotionNode>> = _path

    private val savedEvents = Channel<String>(Channel.BUFFERED)

    /** Emits the saved emotion's label each time [save] completes, for a one-shot toast. */
    val savedEmotionLabel: Flow<String> = savedEvents.receiveAsFlow()

    fun select(child: EmotionNode) {
        _path.update { it + child }
    }

    fun back() {
        _path.update { if (it.size > 1) it.dropLast(1) else it }
    }

    fun save() {
        val leaf = _path.value.last()
        if (!leaf.isLeaf) return
        viewModelScope.launch {
            repository.recordEmotion(leaf.id)
            savedEvents.send(leaf.label)
            _path.value = listOf(root)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { EmotionWheelViewModel(repository()) }
        }
    }
}
