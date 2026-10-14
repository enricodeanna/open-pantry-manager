package eu.studiodeanna.openpantrymanager.memory

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemorySyncTest {
    private val today = LocalDate(2026, 10, 14)

    /** [change], learned on [device] at [at]. */
    private fun Memory.learn(device: String, at: Long, change: (Memory) -> Memory) = changedTo(change(this), Stamp(at, device))

    @Test
    fun eachLearnedThingKeepsItsLatestChangeWhateverTheOrder() {
        val base = Memory().learn("a", 1) { it.placed("Latte", "fridge", 1, today) }
        val onA = base.learn("a", 5) { it.placed("Pasta", "cupboard", 5, today) }
        val onB = base.learn("b", 6) { it.placed("Latte", "door", 6, today).receiptMeans("LATTE P.S. 1L", "Latte") }

        val merged = onA.merge(onB)
        assertEquals(merged, onB.merge(onA))
        assertEquals(merged, merged.merge(onA).merge(onB))
        assertEquals("cupboard", merged.placeFor("pasta", setOf("cupboard", "fridge", "door")))
        // B handled Latte last: its habits are B's.
        assertEquals(onB.food("latte"), merged.food("latte"))
        assertEquals("latte", merged.receipt[receiptKey("LATTE P.S. 1L")])
    }

    @Test
    fun onlyWhatChangedIsStamped() {
        val known = Memory().learn("a", 1) { it.placed("Latte", "fridge", 1, today).receiptSkips("SHOPPER BIO") }
        val next = known.learn("a", 2) { it.moved("Latte", "fridge", "door") }
        assertEquals(setOf("food:latte", "line:shopper bio"), known.stamps.keys)
        assertEquals(Stamp(2, "a"), next.stamps["food:latte"])
        assertEquals(Stamp(2, "a"), next.stamps["moves:fridge"])
        assertEquals(Stamp(1, "a"), next.stamps["line:shopper bio"])
    }

    @Test
    fun aLineLeftOutOnOnePhoneIsLeftOutOnTheOthers() {
        val base = Memory().learn("a", 1) { it.receiptMeans("SHOPPER BIO", "Bag") }
        val onA = base.learn("a", 4) { it.receiptSkips("SHOPPER BIO") }
        val merged = base.merge(onA)
        assertTrue("shopper bio" in merged.skipped)
        assertNull(merged.receipt["shopper bio"])
    }

    @Test
    fun habitsLearnedApartAddUpWhenTwoPantriesBecomeOne() {
        val mine = Memory().placed("Latte", "myFridge", 1, today).placed("Latte", "myFridge", 2, today)
        val shared = Memory().placed("Latte", "door", 3, today).placed("Latte", "fridge", 4, today)
        val joined = shared.combinedWith(mine.withSpaces(mapOf("myFridge" to "fridge")))
        assertEquals("fridge", joined.placeFor("latte", setOf("fridge", "door")))
        assertEquals(4, joined.food("latte")!!.count)
    }

    @Test
    fun whatWasLearnedBeforeStampsStillLoads() {
        val old = """{"foods":{"latte":{"name":"Latte","count":2,"places":{"f1":1.8}}},"receipt":{"latte p s":"latte"},"skipped":["shopper bio"]}"""
        val memory = Json { ignoreUnknownKeys = true }.decodeFromString(Memory.serializer(), old)
        assertEquals("f1", memory.placeFor("Latte", setOf("f1")))
        // Merged with a stamped copy, the stamped one wins where they differ.
        val newer = memory.learn("b", 9) { it.placed("Latte", "f2", 9, today).placed("Latte", "f2", 9, today) }
        assertEquals("f2", memory.merge(newer).placeFor("Latte", setOf("f1", "f2")))
    }
}
