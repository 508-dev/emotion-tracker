package dev.co508.emotiontracker.ui.wheel

import dev.co508.emotiontracker.data.EmotionRepository
import dev.co508.emotiontracker.data.FakeEmotionEntryDao
import dev.co508.emotiontracker.data.testTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmotionWheelViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: EmotionRepository
    private lateinit var viewModel: EmotionWheelViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = EmotionRepository(testTree, FakeEmotionEntryDao())
        viewModel = EmotionWheelViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saving resets wheel and offers the persisted entry for a note`() =
        runTest(dispatcher) {
            viewModel.select(testTree.root.children.first())
            viewModel.save()
            runCurrent()

            val saved = viewModel.savedEmotions.first()
            val entry = repository.observeJournal().first().single()
            assertEquals(entry.id, saved.entryId)
            assertEquals("Positive", saved.label)
            assertEquals(listOf(testTree.root), viewModel.path.value)
            assertNull(entry.note)
        }

    @Test
    fun `quick note targets its entry even after recording the same emotion again`() =
        runTest(dispatcher) {
            viewModel.select(testTree.root.children.first())
            viewModel.save()
            runCurrent()
            val first = viewModel.savedEmotions.first()

            viewModel.select(testTree.root.children.first())
            viewModel.save()
            runCurrent()
            val second = viewModel.savedEmotions.first()

            viewModel.updateNote(first.entryId, "A quiet moment")
            runCurrent()

            val entries = repository.observeJournal().first()
            assertNotEquals(first.entryId, second.entryId)
            assertEquals("A quiet moment", entries.single { it.id == first.entryId }.note)
            assertNull(entries.single { it.id == second.entryId }.note)
        }

    @Test
    fun `uncollected prompts keep only the latest saved entry`() =
        runTest(dispatcher) {
            repeat(2) {
                viewModel.select(testTree.root.children.first())
                viewModel.save()
                runCurrent()
            }

            val entries = repository.observeJournal().first()
            assertEquals(2, entries.size)
            assertEquals(entries.first().id, viewModel.savedEmotions.first().entryId)
            assertTrue(entries.all { it.note == null })
        }

    @Test
    fun `saving the root creates no journal entry`() =
        runTest(dispatcher) {
            viewModel.save()
            runCurrent()

            assertTrue(repository.observeJournal().first().isEmpty())
        }
}
