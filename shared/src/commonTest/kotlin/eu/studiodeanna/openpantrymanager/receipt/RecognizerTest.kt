package eu.studiodeanna.openpantrymanager.receipt

import eu.studiodeanna.openpantrymanager.memory.Memory
import eu.studiodeanna.openpantrymanager.memory.placed
import eu.studiodeanna.openpantrymanager.memory.receiptMeans
import eu.studiodeanna.openpantrymanager.memory.receiptSkips
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RecognizerTest {
    private val today = LocalDate(2026, 10, 14)
    private val spaces = setOf("cabinet", "fridge")

    private fun recognize(memory: Memory, vararg lines: String) =
        Recognizer.recognize(lines.map { ReceiptLine(it) }, memory, spaces, today)

    @Test
    fun aNewLineIsGuessedAndThenKnownOnceTheUserSaidWhatItIs() {
        val first = recognize(Memory(), "PROSC.CRUDO S.DANIELE 100G").single()
        assertEquals(Recognition.Unknown, first.recognition)
        assertEquals("Prosc crudo daniele", first.name)
        assertEquals(null, first.space)

        val memory = Memory().receiptMeans("PROSC.CRUDO S.DANIELE 100G", "Prosciutto crudo")
            .placed("Prosciutto crudo", "fridge", 0, today, LocalDate(2026, 10, 21))
        val second = recognize(memory, "PROSC.CRUDO S.DANIELE 100G").single()
        assertEquals(Recognition.Known, second.recognition)
        assertEquals("Prosciutto crudo", second.name)
        assertEquals("fridge", second.space)
        assertEquals(LocalDate(2026, 10, 21), second.expires)
        // Read again with the words run together, or split elsewhere.
        for (misread in listOf("PROSC.CRUDOS.DANIELE 100G", "PROSC. CRUDO S. DANIELE 100G", "PROSC.CRUDO S.DANIELE100G")) {
            assertEquals(Recognition.Known, recognize(memory, misread).single().recognition, misread)
        }
    }

    @Test
    fun aLineReadingAlmostLikeAKnownOneIsProbable() {
        val memory = Memory().receiptMeans("PROSC.CRUDO S.DANIELE 100G", "Prosciutto crudo")
            .placed("Prosciutto crudo", "fridge", 0, today)
        val entry = recognize(memory, "PROSCIUTTO CRUDO S.DANIELE").single()
        assertEquals(Recognition.Probable, entry.recognition)
        assertEquals("Prosciutto crudo", entry.name)
    }

    @Test
    fun aKnownFoodNamedInTheLineIsProbable() {
        val memory = Memory().placed("Latte", "fridge", 0, today).placed("Latte di soia", "cabinet", 0, today)
        assertEquals("Latte", recognize(memory, "LATTE P.S. ESSELUNGA 1L").single().name)
        assertEquals("Latte di soia", recognize(memory, "LATTE DI SOIA ALPRO").single().name)
    }

    @Test
    fun linesLeftOutBeforeStayOutAndRepeatedLinesAreCountedOnce() {
        val memory = Memory().receiptSkips("SHOPPER BIO")
        val entries = recognize(memory, "SHOPPER BIO", "BANANE", "BANANE")
        assertEquals(listOf(false to Recognition.Skipped, true to Recognition.Unknown), entries.map { it.include to it.recognition })
        assertEquals(2, entries[1].quantity)
    }
}
