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
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextReplacement
import eu.studiodeanna.openpantrymanager.costs.Measure
import eu.studiodeanna.openpantrymanager.costs.MeasureUnit
import eu.studiodeanna.openpantrymanager.costs.livePurchases
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
        val review = repository.understandReceipt(
            listOf(listOf(OcrLine("BISCOTTI FROLLINI 700G", 10f, 100f, 400f, 130f), OcrLine("2,69", 600f, 100f, 680f, 130f))),
        )
        var saved = 0
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                ReceiptScreen(repository, review, onBack = {}, onSaved = { count, _ -> saved = count })
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

    @Test
    fun pricesAreKeptAgainstTheReceiptsTotalAndGoInTheCosts() = runComposeUiTest {
        val repository = PantryRepository(MemoryStore(), CoroutineScope(Dispatchers.Default))
        runBlocking { repository.load() }
        val fridge = repository.addSpace("Fridge")
        val review = repository.understandReceipt(
            listOf(
                listOf(
                    OcrLine("CONAD", 10f, 0f, 200f, 30f),
                    OcrLine("LATTE INTERO 1L", 10f, 100f, 400f, 130f),
                    OcrLine("1,29", 600f, 100f, 680f, 130f),
                    OcrLine("TOTALE 1,39", 10f, 150f, 680f, 180f),
                ),
            ),
        ).let { it.copy(entries = it.entries.map { e -> e.copy(space = fridge) }) }
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                ReceiptScreen(repository, review, onBack = {}, onSaved = { _, _ -> })
            }
        }
        onNodeWithText("The prices add up to €1.29, the receipt says €1.39: check them.").assertExists()
        onAllNodes(hasSetTextAction()).filterToOne(hasText("1.29")).performTextReplacement("1.39")
        onNodeWithText("Total €1.39, as on the receipt.").assertExists()
        onAllNodes(hasSetTextAction()).filterToOne(hasText("Conad")).assertExists()
        onNodeWithText("Save").performClick()
        val purchase = repository.costs.value.livePurchases().single()
        assertEquals(Triple(139L, "Conad", Measure(1000.0, MeasureUnit.Millilitre)), Triple(purchase.cents, purchase.shop, purchase.amount))
    }
}

