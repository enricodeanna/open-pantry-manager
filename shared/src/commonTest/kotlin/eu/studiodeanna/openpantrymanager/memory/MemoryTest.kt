package eu.studiodeanna.openpantrymanager.memory

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemoryTest {
    private val today = LocalDate(2026, 10, 14)
    private val spaces = setOf("cabinet", "fridge", "basement", "freezer")

    private fun Memory.place(name: String, space: String, times: Int = 1): Memory =
        (1..times).fold(this) { m, _ -> m.placed(name, space, 0, today) }

    @Test
    fun suggestsWhereAFoodUsuallyGoesIgnoringCaseAndAccents() {
        val memory = Memory().place("Caffè", "cabinet", 3).place("caffe", "basement")
        assertEquals("cabinet", memory.placeFor("CAFFE", spaces))
        assertNull(memory.placeFor("Tea", spaces))
    }

    @Test
    fun aNewHabitTakesOverAfterAFewTimes() {
        var memory = Memory().place("Milk", "fridge", 10)
        memory = memory.place("Milk", "basement", 2)
        assertEquals("fridge", memory.placeFor("Milk", spaces))
        memory = memory.place("Milk", "basement")
        assertEquals("basement", memory.placeFor("Milk", spaces))
    }

    @Test
    fun forgetsSpacesThatAreGone() {
        val memory = Memory().place("Peas", "freezer", 5).place("Peas", "fridge")
        assertEquals("fridge", memory.placeFor("Peas", spaces - "freezer"))
    }

    @Test
    fun suggestsMovesFromWhatThatFoodDidThenAnyFoodThenItsUsualPlace() {
        var memory = Memory().place("Water", "basement", 2).place("Beer", "basement")
        memory = memory.moved("Water", "basement", "fridge").moved("Water", "basement", "fridge")
        assertEquals("fridge", memory.moveFor("Water", "basement", spaces))
        // Beer was never moved, but things leaving the basement go to the fridge.
        assertEquals("fridge", memory.moveFor("Beer", "basement", spaces))
        // Nothing ever left the cabinet; beer usually is in the basement.
        assertEquals("basement", memory.moveFor("Beer", "cabinet", spaces))
        assertNull(memory.moveFor("Rice", "cabinet", spaces))
    }

    @Test
    fun completesNamesBestFirst() {
        val memory = Memory().place("Pasta", "cabinet", 5).place("Passata", "cabinet", 2).place("Pane", "cabinet", 9)
            .place("Sugo per pasta", "cabinet")
        assertEquals(listOf("Passata"), memory.complete("pass").map { it.name })
        assertEquals(listOf("Pasta", "Sugo per pasta"), memory.complete("past").map { it.name })
        assertEquals(listOf("Pane", "Pasta", "Passata", "Sugo per pasta"), memory.complete("pa").map { it.name })
        assertEquals(listOf("Sugo per pasta"), memory.complete("sugo").map { it.name })
    }

    @Test
    fun learnsHowLongAFoodKeeps() {
        var memory = Memory().placed("Yogurt", "fridge", 0, today, LocalDate(2026, 10, 24))
        assertEquals(10, memory.food("yogurt")?.keepsDays)
        memory = memory.placed("Yogurt", "fridge", 0, today, LocalDate(2026, 10, 28))
        assertEquals(12, memory.food("yogurt")?.keepsDays)
    }

    @Test
    fun receiptLinesAreComparedWithoutNumbers() {
        assertEquals("latte p s esselunga 1l", receiptKey("LATTE P.S. ESSELUNGA 1L"))
        assertEquals(receiptKey("PASSATA 700G 3"), receiptKey("passata 700g"))
    }
}
