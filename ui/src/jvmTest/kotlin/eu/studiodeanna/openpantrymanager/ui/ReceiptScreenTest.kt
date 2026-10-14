package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import eu.studiodeanna.openpantrymanager.PantryRepository
import eu.studiodeanna.openpantrymanager.model.liveItems
import eu.studiodeanna.openpantrymanager.model.visibleSpaces
import eu.studiodeanna.openpantrymanager.receipt.OcrLine
import eu.studiodeanna.openpantrymanager.store.FileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReceiptScreenTest {
    private class MemoryStore : FileStore {
        val files = mutableMapOf<String, String>()
        override suspend fun read(name: String) = files[name]
        override suspend fun write(name: String, content: String) {
            files[name] = content
        }
        override suspend fun delete(name: String) {
            files.remove(name)
        }
    }

    @Test
    fun aSpaceThePantryLacksIsMadeFromTheReviewAndTheFoodGoesThere() = runComposeUiTest {
        val repository = PantryRepository(MemoryStore(), CoroutineScope(Dispatchers.Default))
        runBlocking { repository.load() }
        repository.addSpace("Fridge")
        val entries = repository.understandReceipt(
            listOf(listOf(OcrLine("BISCOTTI FROLLINI 700G", 10f, 100f, 400f, 130f), OcrLine("2,69", 600f, 100f, 680f, 130f))),
        )
        var saved = 0
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                ReceiptScreen(repository, entries, onBack = {}, onSaved = { count, _ -> saved = count })
            }
        }
        // Biscuits don't go in the fridge: nowhere to put them yet.
        onNodeWithText("Save").assertIsNotEnabled()
        onNodeWithText("New space").performClick()
        // The usual spaces the pantry lacks are offered in the dialog.
        onNodeWithText("Kitchen cupboard").performClick()

        val cupboard = repository.pantry.value.visibleSpaces().single { it.name == "Kitchen cupboard" }
        onNodeWithText("Kitchen cupboard").assertIsSelected()
        onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(1, saved)
        assertEquals(listOf("Biscotti frollini" to cupboard.id), repository.pantry.value.liveItems().map { it.name to it.space })
    }
}
