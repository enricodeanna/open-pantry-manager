package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import eu.studiodeanna.openpantrymanager.PantryRepository
import eu.studiodeanna.openpantrymanager.model.liveItems
import eu.studiodeanna.openpantrymanager.store.FileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class PantryScreenTest {
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

    /** As wide as the moto g 5G the app is tried on, with its display size raised (387 dp). */
    @Test
    fun undoAndRedoSitInTheTopBarAndTheTitleStillFits() = runComposeUiTest {
        val repository = PantryRepository(MemoryStore(), CoroutineScope(Dispatchers.Default))
        runBlocking { repository.load() }
        val fridge = repository.addSpace("Frigorifero")
        repository.addItem("Latte", fridge, 2)
        var shoppingOpened = 0
        setContent {
            CompositionLocalProvider(LocalStrings provides ItalianStrings) {
                Box(Modifier.size(387.dp, 800.dp)) {
                    PantryScreen(repository, SnackbarHostState(), onShopping = { shoppingOpened++ }, onScan = {})
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText("Dispensa").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.single().isLineEllipsized(0), "the title is cut")

        onNodeWithContentDescription("Ripeti").assertIsNotEnabled()
        onNodeWithContentDescription("Annulla").assertIsEnabled().performClick()
        assertEquals(emptyList(), repository.pantry.value.liveItems())
        onNodeWithText("Annullato: aggiunta di “Latte”").assertExists()
        onNodeWithContentDescription("Ripeti").assertIsEnabled().performClick()
        assertEquals(listOf("Latte"), repository.pantry.value.liveItems().map { it.name })

        // The shopping list is in the menu; search has its icon, and opens the search field.
        onNodeWithContentDescription("Altro").performClick()
        onNodeWithText("Lista della spesa").assertExists()
        onNodeWithText("Cerca").assertDoesNotExist()
        onNodeWithText("Lista della spesa").performClick()
        assertEquals(1, shoppingOpened)
        onNodeWithContentDescription("Cerca").performClick()
        onNodeWithText("Cerca un alimento").assertExists()
    }

    @Test
    fun aBoxIsOpenedByATapOnItsCountAndOpeningTheLastOneOffersTheShoppingList() = runComposeUiTest {
        val repository = PantryRepository(MemoryStore(), CoroutineScope(Dispatchers.Default))
        runBlocking { repository.load() }
        val cabinet = repository.addSpace("Credenza")
        repository.addItem("Biscotti", cabinet, 2, boxed = true)
        setContent {
            CompositionLocalProvider(LocalStrings provides ItalianStrings) {
                Box(Modifier.size(387.dp, 800.dp)) {
                    PantryScreen(repository, SnackbarHostState(), onShopping = {}, onScan = {})
                }
            }
        }
        onNodeWithContentDescription("Apri una confezione: Biscotti").performClick()
        // The opened box is listed apart, before the closed one; one is still closed, so nothing to buy yet.
        onNodeWithContentDescription("Segna come finita la confezione aperta: Biscotti").assertExists()
        onNodeWithText("Aperto oggi").assertExists()
        onNodeWithText("Aperta l'ultima confezione: Biscotti").assertDoesNotExist()
        assertEquals(listOf(1, 1), repository.pantry.value.liveItems().map { it.quantity })

        onNodeWithContentDescription("Apri una confezione: Biscotti").performClick()
        onNodeWithText("Aperta l'ultima confezione: Biscotti").assertExists()
        assertEquals(0, repository.pantry.value.liveItems().count { it.opened == null })
    }
}
