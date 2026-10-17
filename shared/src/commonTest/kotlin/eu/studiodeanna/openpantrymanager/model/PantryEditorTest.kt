package eu.studiodeanna.openpantrymanager.model

import eu.studiodeanna.openpantrymanager.memory.Memory
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
    fun revertPutsBackWhatAChangeTouchedButNotWhatChangedSince() {
        val (doc, cabinet, fridge) = pantry()
        val (start, water) = editor.addItem(doc, "Water", cabinet, 6)
        val moved = editor.moveItem(start, water, fridge, 2)
        val change = pantryChangeBetween(start, moved, Memory(), Memory())!!
        val undone = editor.revert(moved, change)
        assertEquals(mapOf(cabinet to 6), undone.named("Water").associate { it.space to it.quantity })
        // The revert is an edit of its own, stamped after the move.
        assert(undone.latestStamp() > moved.latestStamp())

        // Once another device has changed the water left in the cabinet, only the bottles moved go back.
        val elsewhere = PantryEditor("other") { 50 }.editItem(moved, water, "Water", 3, null, null)
        val partly = editor.revert(elsewhere, change)
        assertEquals(mapOf(cabinet to 3), partly.named("Water").associate { it.space to it.quantity })
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
    fun itemsAreListedBySoonestExpiryThenByName() {
        val (doc, cabinet, _) = pantry()
        var d = editor.addItem(doc, "beans", cabinet, 1, LocalDate(2027, 1, 1)).first
        d = editor.addItem(d, "Apples", cabinet).first
        d = editor.addItem(d, "Cheese", cabinet, 1, LocalDate(2026, 10, 20)).first
        d = editor.addItem(d, "Butter", cabinet, 1, LocalDate(2026, 10, 20)).first
        assertEquals(listOf("Butter", "Cheese", "beans", "Apples"), d.itemsBySpace()[cabinet]?.map { it.name })
    }

    @Test
    fun foodCountedTogetherKeepsTheDayTheFirstWasBought() {
        val (doc, cabinet, fridge) = pantry()
        val expires = LocalDate(2026, 11, 1)
        var (d, milk) = editor.addItem(doc, "Milk", cabinet, 1, expires, LocalDate(2026, 10, 10))
        d = editor.addItem(d, "Milk", cabinet, 1, expires, LocalDate(2026, 10, 8)).first
        assertEquals(2 to LocalDate(2026, 10, 8), d.item(milk)!!.let { it.quantity to it.bought })
        // Moved, part of it keeps it too.
        d = editor.moveItem(d, milk, fridge, 1)
        assertEquals(listOf(LocalDate(2026, 10, 8), LocalDate(2026, 10, 8)), d.liveItems().map { it.bought })
    }
}
