package eu.studiodeanna.openpantrymanager.checklist

import kotlin.test.Test
import kotlin.test.assertEquals

class SimilarItemsTest {
    private fun list(vararg items: Pair<String, Boolean>) = ListDocument(
        items = items.mapIndexed { i, (text, done) -> Item("item$i", text, done = done, pos = i.toDouble(), at = 1, by = "a") },
    )

    private fun ListDocument.similar(text: String) = similarItems(text).map { it.text }

    @Test
    fun theSameNameComesFirstThenNamesSharingWords() {
        val doc = list("Latte" to true, "Latte di soia" to false, "Lattuga" to false, "Caffè" to false)
        assertEquals(listOf("Latte", "Latte di soia"), doc.similar("latte"))
        assertEquals(listOf("Latte", "Latte di soia"), doc.similar("Latte intero fresco"))
        assertEquals(listOf("Caffè"), doc.similar("CAFFE macinato"))
    }

    @Test
    fun aWordMatchesItsAbbreviationAndAOneLetterSlip() {
        val doc = list("Mele" to false, "Prosciutto crudo" to true, "Pasta" to false)
        assertEquals(listOf("Mele"), doc.similar("Mela"))
        assertEquals(listOf("Prosciutto crudo"), doc.similar("Prosc crudo"))
        // Short words such as "di" do not make names alike.
        assertEquals(emptyList(), doc.similar("Succo di frutta"))
    }
}
