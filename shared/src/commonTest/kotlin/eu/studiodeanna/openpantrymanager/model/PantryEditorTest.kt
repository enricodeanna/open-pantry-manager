package eu.studiodeanna.openpantrymanager.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PantryEditorTest {
    private var clock = 1000L
    private val editor = PantryEditor("dev1") { clock++ }

    private fun pantry(): Triple<PantryDocument, String, String> {
        val (a, cabinet) = editor.addSpace(PantryDocument(), "Cabinet")
        val (b, fridge) = editor.addSpace(a, "Fridge")
        return Triple(b, cabinet, fridge)
    }

    private fun PantryDocument.named(name: String) = liveItems().filter { it.name == name }

    @Test
    fun theSameFoodWithTheSameDateIsCountedNotListedTwice() {
        val (doc, cabinet, fridge) = pantry()
        val date = LocalDate(2026, 11, 1)
        var d = editor.addItem(doc, "Tuna", cabinet, 2).first
        d = editor.addItem(d, "tuna ", cabinet, 1).first
        d = editor.addItem(d, "Tuna", cabinet, 1, date).first
        d = editor.addItem(d, "Tuna", fridge).first
        assertEquals(listOf(3 to null, 1 to date), d.named("Tuna").filter { it.space == cabinet }.map { it.quantity to it.expires })
        assertEquals(1, d.named("Tuna").single { it.space == fridge }.quantity)
    }

    @Test
    fun movingSomeSplitsAndMovingAllJoinsWhatIsThere() {
        val (doc, cabinet, fridge) = pantry()
        var (d, water) = editor.addItem(doc, "Water", cabinet, 6)
        d = editor.moveItem(d, water, fridge, 2)
        assertEquals(mapOf(cabinet to 4, fridge to 2), d.named("Water").associate { it.space to it.quantity })
        d = editor.moveItem(d, water, fridge)
        assertEquals(mapOf(fridge to 6), d.named("Water").associate { it.space to it.quantity })
    }

    @Test
    fun usingTheLastOneTakesItOut() {
        val (doc, cabinet, _) = pantry()
        var (d, id) = editor.addItem(doc, "Eggs", cabinet, 2)
        d = editor.useOne(d, id)
        assertEquals(1, d.item(id)?.quantity)
        d = editor.useOne(d, id)
        assertNull(d.item(id))
    }

    @Test
    fun restorePutsBackWhatAChangeTouched() {
        val (doc, cabinet, fridge) = pantry()
        val (start, water) = editor.addItem(doc, "Water", cabinet, 6)
        val moved = editor.moveItem(start, water, fridge, 2)
        val undone = editor.restore(moved, changedItems(start, moved))
        assertEquals(mapOf(cabinet to 6), undone.named("Water").associate { it.space to it.quantity })
        // The restore is an edit of its own, stamped after the move.
        assert(undone.latestStamp() > moved.latestStamp())
    }

    @Test
    fun deletingASpaceTakesItsFoodWithIt() {
        val (doc, cabinet, fridge) = pantry()
        var d = editor.addItem(doc, "Rice", cabinet).first
        d = editor.addItem(d, "Milk", fridge).first
        d = editor.deleteSpace(d, cabinet)
        assertEquals(listOf("Milk"), d.liveItems().map { it.name })
        assertEquals(listOf("Fridge"), d.visibleSpaces().map { it.name })
    }

    @Test
    fun itemsAreListedByNameOrBySoonestExpiry() {
        val (doc, cabinet, _) = pantry()
        var d = editor.addItem(doc, "beans", cabinet, 1, LocalDate(2027, 1, 1)).first
        d = editor.addItem(d, "Apples", cabinet).first
        d = editor.addItem(d, "Cheese", cabinet, 1, LocalDate(2026, 10, 20)).first
        assertEquals(listOf("Apples", "beans", "Cheese"), d.itemsBySpace(ItemOrder.Name)[cabinet]?.map { it.name })
        assertEquals(listOf("Cheese", "beans", "Apples"), d.itemsBySpace(ItemOrder.Expiry)[cabinet]?.map { it.name })
    }
}
