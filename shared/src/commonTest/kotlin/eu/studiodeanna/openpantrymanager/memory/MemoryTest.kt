package eu.studiodeanna.openpantrymanager.memory

import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.DatePeriod
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
    fun aFoodKeepsTheMiddleOfItsLastFiveTimesCountedFromTheDayItWasBought() {
        var memory = Memory()
        val bought = LocalDate(2026, 10, 1)
        // A slip of the finger (30 days) does not pull the middle away.
        for (days in listOf(7, 8, 30, 7, 9)) {
            memory = memory.placed("Latte", "fridge", 0, today, bought.plus(DatePeriod(days = days)), bought)
        }
        assertEquals(8, memory.food("latte")?.keepsDays)
        // Only the last five count: a new brand that keeps longer takes over.
        repeat(3) { memory = memory.placed("Latte", "fridge", 0, today, bought.plus(DatePeriod(days = 12)), bought) }
        assertEquals(listOf(7, 9, 12, 12, 12), memory.food("latte")?.keeps)
        assertEquals(12, memory.food("latte")?.keepsDays)
    }

    @Test
    fun aKeepingTimeLearnedBeforeCountsAsOneTime() {
        val before = Memory(foods = mapOf("latte" to Food("Latte", keepsDays = 12)))
        val after = before.placed("Latte", "fridge", 0, today, today.plus(DatePeriod(days = 8)))
        assertEquals(listOf(12, 8), after.food("latte")?.keeps)
        assertEquals(10, after.food("latte")?.keepsDays)
    }

    @Test
    fun receiptLinesAreComparedWithoutNumbers() {
        assertEquals("latte p s esselunga 1l", receiptKey("LATTE P.S. ESSELUNGA 1L"))
        assertEquals(receiptKey("PASSATA 700G 3"), receiptKey("passata 700g"))
    }
}
