package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import eu.studiodeanna.openpantrymanager.PantryRepository
import eu.studiodeanna.openpantrymanager.checklist.Item
import eu.studiodeanna.openpantrymanager.checklist.ListCodec
import eu.studiodeanna.openpantrymanager.checklist.ListDocument
import eu.studiodeanna.openpantrymanager.currentTimeMillis
import eu.studiodeanna.openpantrymanager.store.DeviceState
import eu.studiodeanna.openpantrymanager.sync.ShareLink
import kotlinx.serialization.json.Json
import eu.studiodeanna.openpantrymanager.model.liveItems
import eu.studiodeanna.openpantrymanager.store.FileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.studiodeanna.openpantrymanager.Cooked
import eu.studiodeanna.openpantrymanager.costs.Measure
import eu.studiodeanna.openpantrymanager.costs.MeasureUnit
import eu.studiodeanna.openpantrymanager.model.visibleSpaces
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

    @Test
    fun aSearchThatFindsNothingOffersToAddTheFoodOrPutItOnTheList() = runComposeUiTest {
        val repository = PantryRepository(MemoryStore(), CoroutineScope(Dispatchers.Default))
        runBlocking { repository.load() }
        val fridge = repository.addSpace("Frigorifero")
        repository.addItem("Latte", fridge)
        setContent {
            CompositionLocalProvider(LocalStrings provides ItalianStrings) {
                Box(Modifier.size(387.dp, 800.dp)) {
                    PantryScreen(repository, SnackbarHostState(), onShopping = {}, onScan = {})
                }
            }
        }
        onNodeWithContentDescription("Cerca").performClick()
        onNode(hasSetTextAction()).performTextInput("burro")
        onNodeWithText("Nessun alimento si chiama “burro”.").assertExists()
        onNodeWithText("Metti “Burro” nella lista della spesa").assertExists()
        // The X clears what was typed at once, and the search stays open.
        onNodeWithContentDescription("Cancella").performClick()
        onNodeWithText("Nessun alimento si chiama “burro”.").assertDoesNotExist()
        onNodeWithContentDescription("Cancella").assertDoesNotExist()
        onNodeWithContentDescription("Chiudi la ricerca").assertExists()
        onNode(hasSetTextAction()).performTextInput("burro")
        onNodeWithText("Aggiungi “Burro” alla dispensa").performClick()
        onNodeWithText("Aggiungi alimento").assertExists()
        onAllNodes(hasSetTextAction()).filterToOne(hasText("Burro")).assertExists()
    }

    @Test
    fun whatWasTickedOnTheShoppingListIsOfferedToPutAway() = runComposeUiTest {
        // A shopping list linked before, with milk ticked just now and bread long ago.
        val store = MemoryStore()
        val now = currentTimeMillis()
        store.files["device.json"] = Json.encodeToString(
            DeviceState.serializer(),
            DeviceState("phone001", shoppingLink = ShareLink("https://cloud.example.com/s/AbC123", fileName = "Spesa.json")),
        )
        store.files["shopping-list.json"] = ListCodec.encode(
            ListDocument(
                title = "Spesa",
                items = listOf(
                    Item("milk", "Latte", done = true, pos = 1.0, at = now, by = "other"),
                    Item("bread", "Pane", done = true, pos = 2.0, at = now - 10L * 24 * 60 * 60 * 1000, by = "other"),
                ),
            ),
        )
        val repository = PantryRepository(store, CoroutineScope(Dispatchers.Default))
        runBlocking { repository.load() }
        repository.addSpace("Frigorifero")
        var putAway: List<Item>? = null
        setContent {
            CompositionLocalProvider(LocalStrings provides ItalianStrings) {
                Box(Modifier.size(387.dp, 800.dp)) {
                    PantryScreen(repository, SnackbarHostState(), onShopping = {}, onScan = {}, onPutAway = { putAway = it })
                }
            }
        }
        onNodeWithText("Hai spuntato 1 prodotto nella lista della spesa.").assertExists()
        onNodeWithText("Mettili in dispensa").performClick()
        assertEquals(listOf("milk"), putAway?.map { it.id })
        onNodeWithText("Ignora").performClick()
        onNodeWithText("Hai spuntato 1 prodotto nella lista della spesa.").assertDoesNotExist()
        assertEquals(emptyList(), repository.boughtOnList())
    }

    @Test
    fun aLongPressMarksLeftoversAndAnotherUnmarksThem() = runComposeUiTest {
        val repository = PantryRepository(MemoryStore(), CoroutineScope(Dispatchers.Default))
        runBlocking { repository.load() }
        val fridge = repository.addSpace("Frigorifero")
        repository.addItem("Lasagne", fridge)
        setContent {
            CompositionLocalProvider(LocalStrings provides ItalianStrings) {
                Box(Modifier.size(387.dp, 800.dp)) {
                    PantryScreen(repository, SnackbarHostState(), onShopping = {}, onScan = {})
                }
            }
        }
        onNodeWithText("Lasagne").performTouchInput { longClick() }
        onNodeWithText("Avanzi").assertExists()
        assertEquals(true, repository.pantry.value.liveItems().single().leftover)
        onNodeWithText("Lasagne").performTouchInput { longClick() }
        onNodeWithText("Avanzi").assertDoesNotExist()
        assertEquals(false, repository.pantry.value.liveItems().single().leftover)
    }

    @Test
    fun aRecipeTakesWhatIsTickedAndIsListedToChange() = runComposeUiTest {
        val repository = PantryRepository(MemoryStore(), CoroutineScope(Dispatchers.Default))
        runBlocking { repository.load() }
        val fridge = repository.addSpace("Frigorifero")
        repository.addItem("Uova", fridge, 6)
        repository.addItem("Farina", fridge, 1, boxed = true, size = Measure(1000.0, MeasureUnit.Gram))
        var done: Cooked? = null
        var screen by mutableStateOf("cook")
        setContent {
            CompositionLocalProvider(LocalStrings provides ItalianStrings) {
                Box(Modifier.size(387.dp, 800.dp)) {
                    if (screen == "cook") CookScreen(repository, "Crêpes", onClose = {}) { done = it } else RecipesScreen(repository, onBack = {}, onCook = {})
                }
            }
        }
        onNodeWithText("Salva").assertIsNotEnabled()
        onNodeWithText("Uova").performClick()
        onNodeWithContentDescription("Altro").performClick()
        onNodeWithText("Farina").performClick()
        // A box ticked is opened, some left; how much went in, typed.
        onNodeWithText("Usati").performTextInput("250")
        onNodeWithText("Salva").assertIsEnabled().performClick()
        // Its only box opened, the flour has run out: with no list linked, nothing is asked.
        assertEquals(listOf("Farina"), done?.ranOut)
        val items = repository.pantry.value.liveItems().associateBy { it.name }
        assertEquals(4, items.getValue("Uova").quantity)
        assertEquals(750.0, items.getValue("Farina").left)

        // Listed, and renamed.
        screen = "recipes"
        onNodeWithText("Crêpes").performClick()
        onAllNodes(hasSetTextAction()).filterToOne(hasText("Crêpes")).performTextReplacement("Crespelle")
        onNodeWithText("Salva").performClick()
        onNodeWithText("Crespelle").assertExists()
        assertEquals(listOf("Uova", "Farina"), repository.recipe("crespelle")?.ingredients?.map { it.food })
    }

    @Test
    fun theDishCanBePutAwayAsLeftoversWhereChosen() = runComposeUiTest {
        val repository = PantryRepository(MemoryStore(), CoroutineScope(Dispatchers.Default))
        runBlocking { repository.load() }
        repository.addSpace("Frigorifero")
        val freezer = repository.addSpace("Congelatore")
        repository.addItem("Riso", freezer, 1)
        setContent {
            CompositionLocalProvider(LocalStrings provides ItalianStrings) {
                Box(Modifier.size(387.dp, 800.dp)) { CookScreen(repository, "Risotto", onClose = {}, onDone = {}) }
            }
        }
        onNodeWithText("Riso").performClick()
        onNodeWithText("Avanzi").performClick()
        // A place to put it is needed first.
        onNodeWithText("Salva").assertIsNotEnabled()
        onNodeWithText("Frigorifero").performClick()
        onNodeWithText("Salva").assertIsEnabled().performClick()
        val risotto = repository.pantry.value.liveItems().single { it.name == "Risotto" }
        assertEquals(true, risotto.leftover)
        assertEquals("Frigorifero", repository.pantry.value.visibleSpaces().single { it.id == risotto.space }.name)
    }
}
