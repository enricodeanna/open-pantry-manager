package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import eu.studiodeanna.openpantrymanager.ShoppingList
import eu.studiodeanna.openpantrymanager.Sublist
import eu.studiodeanna.openpantrymanager.SyncState
import eu.studiodeanna.openpantrymanager.checklist.Item
import eu.studiodeanna.openpantrymanager.checklist.ListDocument
import eu.studiodeanna.openpantrymanager.checklist.Section
import eu.studiodeanna.openpantrymanager.checklist.similarItems
import eu.studiodeanna.openpantrymanager.sync.ShareLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class ShoppingPromptTest {
    private val list = ShoppingList(
        ShareLink("https://cloud.example.com/s/AbC123", fileName = "Spesa.json"),
        ListDocument(
            title = "Spesa",
            sections = listOf(Section("dairy", "Latticini", 1.0, 1, "a")),
            items = listOf(Item("milk", "Latte", section = "dairy", done = true, pos = 1.0, at = 1, by = "a")),
        ),
        SyncState.Synced(0),
    )

    private fun prompt(food: String, initialText: String = food, onAdd: (String, Sublist) -> Unit = { _, _ -> }, onUse: (Item) -> Unit = {}) =
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                    ShoppingPromptDialog(
                        name = food,
                        shopping = list,
                        suggested = null,
                        similar = { list.doc.similarItems(it) },
                        initialText = initialText,
                        onDismiss = {},
                        onLink = {},
                        onAdd = onAdd,
                        onUse = onUse,
                    )
                }
            }
            onNodeWithText("Latte · Latticini · ticked, put it back").performClick()
            onNodeWithText("Add").performClick()
        }

    @Test
    fun aSimilarItemOnTheListIsOfferedAndPutBack() {
        var used: Item? = null
        prompt("Latte p.s. Friuli", onUse = { used = it })
        assertEquals("milk", used?.id)
    }

    @Test
    fun theNameOnTheListCanBeChangedForANewItem() = runComposeUiTest {
        var added: Pair<String, Sublist>? = null
        var used: Item? = null
        setContent {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                ShoppingPromptDialog(
                    name = "Pane comune",
                    shopping = list,
                    suggested = null,
                    similar = { list.doc.similarItems(it) },
                    onDismiss = {},
                    onLink = {},
                    onAdd = { text, sublist -> added = text to sublist },
                    onUse = { used = it },
                )
            }
        }
        onNode(hasSetTextAction()).performTextReplacement("Pane")
        onNodeWithText("Latticini").performClick()
        onNodeWithText("Add").performClick()
        assertEquals("Pane" to Sublist.Existing("dairy"), added)
        assertNull(used)
    }
}
