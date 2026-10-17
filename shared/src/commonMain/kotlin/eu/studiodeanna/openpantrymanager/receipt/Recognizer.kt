package eu.studiodeanna.openpantrymanager.receipt

import eu.studiodeanna.openpantrymanager.memory.Memory
import eu.studiodeanna.openpantrymanager.memory.placeFor
import eu.studiodeanna.openpantrymanager.memory.receiptKey
import eu.studiodeanna.openpantrymanager.model.nameKey
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** How sure the app is about what a receipt line is. */
enum class Recognition {
    /** The user said so on an earlier receipt. */
    Known,
    /** It looks like a line or a food the app knows; the user should check. */
    Probable,
    /** Never seen: the name is only tidied up from the receipt, and the user is asked what it is. */
    Unknown,
    /** The user left this line out before (a bag, a deposit); it stays out unless they take it in. */
    Skipped,
}

/**
 * A receipt line as the app understood it, ready for the user to check: what food it is, how many,
 * where it goes and, for foods whose keeping time it has learned, until when they keep.
 */
data class ReceiptEntry(
    val line: ReceiptLine,
    val name: String,
    val recognition: Recognition,
    val quantity: Int,
    val space: String?,
    val expires: LocalDate?,
    val include: Boolean,
)

object Recognizer {
    /**
     * Understands the [lines] of a receipt with what [memory] holds. [spaces] are the live space ids;
     * [today] is the shopping day, from which expiry days are reckoned. The same line printed twice
     * becomes one entry with both counted.
     */
    fun recognize(lines: List<ReceiptLine>, memory: Memory, spaces: Set<String>, today: LocalDate): List<ReceiptEntry> {
        val merged = LinkedHashMap<String, ReceiptLine>()
        for (line in lines) {
            val key = receiptKey(line.text).ifEmpty { line.text }
            merged[key] = merged[key]?.let { it.copy(quantity = it.quantity + line.quantity) } ?: line
        }
        return merged.values.map { line -> entry(line, memory, spaces, today) }
    }

    private fun entry(line: ReceiptLine, memory: Memory, spaces: Set<String>, today: LocalDate): ReceiptEntry {
        val key = remembered(receiptKey(line.text), memory)
        val (name, recognition) = when {
            key in memory.skipped -> guessName(line.text) to Recognition.Skipped
            else -> memory.receipt[key]?.let { memory.foods[it] }?.let { it.name to Recognition.Known }
                ?: similarLine(key, memory)?.let { it to Recognition.Probable }
                ?: foodNamed(key, memory)?.let { it to Recognition.Probable }
                ?: (guessName(line.text) to Recognition.Unknown)
        }
        val pack = if (recognition == Recognition.Known) memory.packs[key] ?: 1 else 1
        val food = memory.foods[nameKey(name)]
        return ReceiptEntry(
            line = line,
            name = name,
            recognition = recognition,
            quantity = line.quantity * pack,
            space = memory.placeFor(name, spaces),
            expires = food?.keepsDays?.let { today.plus(DatePeriod(days = it)) },
            include = recognition != Recognition.Skipped,
        )
    }

    /**
     * The remembered line [key] is, if any, as the text reader may run words together or split them
     * ("DETERSIVOPIATTI", "FARINA 001KG"): compared by their letters alone. Otherwise [key] itself.
     */
    private fun remembered(key: String, memory: Memory): String {
        if (key in memory.receipt || key in memory.skipped) return key
        fun letters(line: String) = line.filter { it.isLetter() }
        val compact = letters(key)
        return (memory.receipt.keys + memory.skipped).firstOrNull { letters(it) == compact } ?: key
    }

    /**
     * The food of a remembered line that reads almost like this one, as the same product often prints
     * a little differently in another shop or after the text recognizer misreads a letter.
     */
    private fun similarLine(key: String, memory: Memory): String? {
        val words = key.split(' ')
        val (best, score) = memory.receipt.entries
            .map { (line, food) -> food to similarity(words, line.split(' ')) }
            .maxByOrNull { it.second } ?: return null
        return memory.foods[best]?.name?.takeIf { score >= SIMILAR }
    }

    /** A known food all of whose words are in the line ("Latte" in "LATTE P.S. ESSELUNGA"); the longest name wins. */
    private fun foodNamed(key: String, memory: Memory): String? {
        val words = key.split(' ')
        return memory.foods.entries
            .filter { (food, _) -> food.split(' ').all { word -> words.any { sameWord(it, word) } } }
            .maxByOrNull { it.key.length }
            ?.value?.name
    }

    /** Shared words over all words; a word and its abbreviation ("prosc", "prosciutto") count as shared. */
    private fun similarity(a: List<String>, b: List<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val shared = a.count { x -> b.any { sameWord(x, it) } }
        return shared.toDouble() / maxOf(a.size, b.size)
    }

    private fun sameWord(a: String, b: String): Boolean = when {
        a == b -> true
        a.length >= 3 && b.length >= 3 -> a.startsWith(b) || b.startsWith(a)
        else -> false
    }

    private const val SIMILAR = 0.67
}
