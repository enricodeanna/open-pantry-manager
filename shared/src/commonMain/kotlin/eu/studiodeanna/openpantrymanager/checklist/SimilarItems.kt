package eu.studiodeanna.openpantrymanager.checklist

import eu.studiodeanna.openpantrymanager.model.editDistance
import eu.studiodeanna.openpantrymanager.model.nameKey

/**
 * The items of the list that may be [text] already, closest first: the same name, ignoring case,
 * accents and spacing, then names that share words with it ("Latte" for "Latte intero"). A word
 * matches its abbreviation ("prosc", "prosciutto") and a one-letter slip ("mela", "mele"); words of
 * two letters ("di", "al") do not count. Ticked items are among them, to be put back on the list.
 */
fun ListDocument.similarItems(text: String, limit: Int = 3): List<Item> {
    val key = nameKey(text)
    if (key.isEmpty()) return emptyList()
    val words = meaningful(key)
    return items.asSequence().filterNot { it.deleted }.mapNotNull { item ->
        val other = nameKey(item.text)
        val score = if (other == key) {
            EXACT
        } else {
            val otherWords = meaningful(other)
            val shared = words.count { word -> otherWords.any { sameWord(word, it) } }
            // Most of the shorter name is in the longer one; the closer in length, the better.
            if (shared * 2 < minOf(words.size, otherWords.size) || shared == 0) return@mapNotNull null
            shared.toDouble() / minOf(words.size, otherWords.size) + shared.toDouble() / maxOf(words.size, otherWords.size)
        }
        item to score
    }.sortedWith(compareBy({ -it.second }, { it.first.done }, { it.first.text.lowercase() }))
        .take(limit)
        .map { it.first }
        .toList()
}

private const val EXACT = 3.0

private fun meaningful(key: String): List<String> {
    val words = key.split(' ')
    return words.filter { it.length >= 3 }.ifEmpty { words }
}

private fun sameWord(a: String, b: String): Boolean {
    if (a == b) return true
    if (minOf(a.length, b.length) < 4) return false
    return a.startsWith(b) || b.startsWith(a) || (a[0] == b[0] && editDistance(a, b) <= 1)
}
