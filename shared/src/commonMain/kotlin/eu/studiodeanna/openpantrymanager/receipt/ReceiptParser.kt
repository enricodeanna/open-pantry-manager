package eu.studiodeanna.openpantrymanager.receipt

import eu.studiodeanna.openpantrymanager.model.editDistance
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * One line of text the platform's text recognizer found in a photo, with its box in pixels and the
 * angle (degrees) the line is tilted by. Receipts are read from these.
 */
data class OcrLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val angle: Float = 0f,
)

/**
 * A product line of a receipt: its description as printed, how many were bought, and its price, less
 * the discounts printed for it; for food sold by weight, the [kilograms] weighed.
 */
data class ReceiptLine(val text: String, val quantity: Int = 1, val price: Double? = null, val kilograms: Double? = null)

/**
 * A receipt as read: its product lines, the [total] it prints, to check them against, and its [header],
 * the rows above the products, which name the shop.
 */
data class Receipt(val lines: List<ReceiptLine>, val total: Double? = null, val header: String = "") {
    /** The row of the header that names the shop: the first row of words that is not an address or a tax number. */
    val shopRow: String? get() = header.split('\n').firstOrNull(::namesShop)

    /** The shop's name as the header prints it, tidied. */
    val shop: String? get() = shopRow?.let(::tidyShop)
}

/**
 * Turns what a text recognizer saw on a shop receipt into its product lines. Receipts print the
 * description on the left and the price on the right, which recognizers often return as separate
 * lines; they are put back into printed rows by their height on the paper first.
 */
object ReceiptParser {
    fun parse(lines: List<OcrLine>): List<ReceiptLine> = parseRows(rows(lines))

    /**
     * The product lines of a receipt taken in several photos, top to bottom ([pages]). The rows a photo
     * repeats from the one before are taken once.
     */
    fun parsePages(pages: List<List<OcrLine>>): List<ReceiptLine> = readPages(pages).lines

    /** The receipt taken in several photos, top to bottom ([pages]): its products, total and header. */
    fun readPages(pages: List<List<OcrLine>>): Receipt = readRows(joinPages(pages.map(::rows)))

    /**
     * The printed rows of photos taken one after another down a long receipt, without the rows each
     * photo repeats from the one before. Where a photo goes on from is found by the longest run of rows
     * it begins with that the photos before end with. A row cut by a photo's edge is read badly or not at
     * all, so the run may leave up to two rows at the end of the one and the start of the other; those
     * are dropped, as the other photo has them whole. Of the rows both show, each is taken from the photo
     * that has it further from its edge: the first half from the photo before, the rest from the next.
     *
     * Two of a food rung up one by one print the same row twice. If a photo ends between them, the
     * second can be taken for a repeat and left out; the review shows the count, to put right.
     */
    fun joinPages(pages: List<List<String>>): List<String> {
        val joined = mutableListOf<String>()
        for (page in pages) {
            val seam = seam(joined, page)
            if (seam == null) {
                joined += page
            } else {
                val (keep, skip) = seam
                while (joined.size > keep) joined.removeAt(joined.size - 1)
                joined += page.drop(skip)
            }
        }
        return joined
    }

    /**
     * Where [next] goes on from [before]: how many rows of [before] to keep, and how many of [next] to
     * leave out. Null if they share no rows.
     */
    private fun seam(before: List<String>, next: List<String>): Pair<Int, Int>? {
        val a = before.map(::comparable)
        val b = next.map(::comparable)
        // Where the run starts in each, and how long it is.
        var best: Triple<Int, Int, Int>? = null
        for (i in maxOf(0, a.size - OVERLAP_WINDOW) until a.size) {
            for (j in 0..minOf(SEAM_SLACK, b.size - 1)) {
                var run = 0
                var letters = 0
                while (i + run < a.size && j + run < b.size && similar(a[i + run], b[j + run])) {
                    letters += a[i + run].length
                    run++
                }
                // The run must reach the end of the photos before, but for rows cut by the edge.
                if (run == 0 || a.size - (i + run) > SEAM_SLACK || (run == 1 && letters < MIN_SEAM_LETTERS)) continue
                val current = best
                // The longest run; of runs as long, the one that drops fewest rows at the edges.
                if (current == null || run > current.third || (run == current.third && i + run - j > current.first + current.third - current.second)) {
                    best = Triple(i, j, run)
                }
            }
        }
        return best?.let { (i, j, run) -> (i + run / 2) to (j + run / 2) }
    }

    /** A row as compared between photos: its letters and digits, without the spaces a reader may add or drop. */
    private fun comparable(row: String) = row.uppercase().filter { it.isLetterOrDigit() }

    /** Whether two rows are the same printed row, read twice: at most one character in five differs. */
    private fun similar(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (minOf(a.length, b.length) <= 3) return a == b
        return editDistance(a, b) * 5 <= maxOf(a.length, b.length)
    }

    /** The printed rows, top to bottom, each one's pieces joined left to right. */
    fun rows(lines: List<OcrLine>): List<String> {
        val usable = lines.filter { it.text.isNotBlank() && it.bottom > it.top }
        if (usable.isEmpty()) return emptyList()
        // Undo the photo's tilt, so a row's price sits as high as its description.
        val tilt = usable.filter { it.right - it.left > 3 * (it.bottom - it.top) }.map { it.angle }.median() ?: 0f
        val radians = -tilt * kotlin.math.PI / 180
        val height = usable.map { it.bottom - it.top }.median()!!
        data class Placed(val line: OcrLine, val x: Double, val y: Double)
        val placed = usable.map {
            val cx = (it.left + it.right) / 2.0
            val cy = (it.top + it.bottom) / 2.0
            Placed(it, cx * cos(radians) - cy * sin(radians), cx * sin(radians) + cy * cos(radians))
        }.sortedBy { it.y }
        val rows = mutableListOf<MutableList<Placed>>()
        for (p in placed) {
            val row = rows.lastOrNull()
            if (row != null && abs(p.y - row.sumOf { it.y } / row.size) < height * 0.5) row += p else rows += mutableListOf(p)
        }
        return rows.map { row -> row.sortedBy { it.x }.joinToString("  ") { it.line.text.trim() } }
    }

    /** The product lines among printed rows. */
    fun parseRows(rows: List<String>): List<ReceiptLine> = readRows(rows).lines

    /** The receipt the printed rows make: its product lines, the total it prints, and the rows above the products. */
    fun readRows(rows: List<String>): Receipt {
        val found = mutableListOf<Found>()
        val quantities = mutableListOf<Pair<Int, Quantity>>()
        val weights = mutableListOf<Pair<Int, Weight>>()
        val header = mutableListOf<String>()
        var total: Double? = null
        // A description whose price comes on the next row.
        var pendingName: String? = null
        for (raw in rows) {
            val row = raw.trim().replace(Regex("\\s+"), " ")
            if (row.isEmpty()) continue
            val upper = row.uppercase()
            if (END.containsMatchIn(upper)) {
                if (TOTAL.containsMatchIn(upper)) total = PRICE_AT_END.find(row)?.groupValues?.get(1)?.let(::price)
                break
            }
            if (found.isEmpty() && quantities.isEmpty() && PRICE_AT_END.find(row) == null) header += row
            val quantity = QUANTITY.find(upper)
            val name = pendingName
            pendingName = null
            when {
                quantity != null -> {
                    val count = quantity.groupValues[1].toInt()
                    val unit = price(quantity.groupValues[2])
                    val total = PRICE_AT_END.find(upper.substring(quantity.range.last + 1))?.groupValues?.get(1)?.let(::price)
                    when {
                        count !in 1..MAX_QUANTITY -> Unit
                        // "YOGURT" then "2 x 0,89  1,78": the row before is the product.
                        name != null && total != null -> found += Found(name, count, total)
                        else -> quantities += found.size to Quantity(count, unit)
                    }
                }
                WEIGHT.containsMatchIn(upper) -> {
                    val weighed = KILOGRAMS.find(upper)
                    val kilograms = weighed?.groupValues?.get(1)?.let(::price)?.takeIf { it > 0 } ?: continue
                    val perKilogram = PER_KILOGRAM.find(upper)
                    // A price after the price per kilogram is the product's own.
                    val price = PRICE_AT_END.find(upper)
                        ?.takeIf { it.range.first > maxOf(weighed.range.last, perKilogram?.range?.last ?: 0) }
                        ?.groupValues?.get(1)?.let(::price)
                    when {
                        // "BANANE" then "0,846 kg x 1,52 €/kg  1,29": the row before is the product.
                        name != null && price != null -> found += Found(name, 1, price, kilograms)
                        else -> weights += found.size to Weight(kilograms, perKilogram?.groupValues?.get(1)?.let(::price))
                    }
                }
                SKIP.containsMatchIn(upper) -> Unit
                else -> {
                    val priceMatch = PRICE_AT_END.find(row)
                    if (priceMatch == null) {
                        pendingName = row.takeIf { letters(it) >= 3 }
                        continue
                    }
                    val amount = price(priceMatch.groupValues[1])
                    val description = row.substring(0, priceMatch.range.first).replace(VAT, "").trim()
                    when {
                        amount == null -> Unit
                        // A discount lowers the price of the product just before it.
                        amount < 0 || DISCOUNT.containsMatchIn(upper) -> found.lastOrNull()?.let { discount(it, priceMatch.groupValues[1]) }
                        letters(description) >= 2 -> found += Found(description, 1, amount)
                        // A price alone on its row belongs to the description just before it.
                        description.isBlank() && name != null -> found += Found(name, 1, amount)
                    }
                }
            }
        }
        // "2 x 0,89" belongs to the product before or after it: the one whose price it adds up to.
        for ((at, quantity) in quantities) {
            val before = found.getOrNull(at - 1)
            val after = found.getOrNull(at)
            val total = quantity.unit?.times(quantity.count)
            val target = when {
                total != null && after?.price?.let { abs(it - total) < 0.011 } == true -> after
                total != null && before?.price?.let { abs(it - total) < 0.011 } == true -> before
                after != null -> after
                else -> before
            }
            target?.count = quantity.count
        }
        // "0,846 kg x 1,52 €/kg" belongs likewise to the product whose price it makes.
        for ((at, weight) in weights) {
            val before = found.getOrNull(at - 1)
            val after = found.getOrNull(at)
            val made = weight.perKilogram?.times(weight.kilograms)
            // With the price per kilogram printed, only the product it makes the price of; else the one before.
            val target = when {
                made == null -> before
                after?.price?.let { abs(it - made) < 0.02 } == true -> after
                before?.price?.let { abs(it - made) < 0.02 } == true -> before
                else -> null
            }
            target?.kilograms = weight.kilograms
        }
        return Receipt(
            found.map { ReceiptLine(it.text, it.count, it.price?.let { p -> kotlin.math.round(p * 100) / 100 }, it.kilograms) },
            total,
            header.joinToString("\n"),
        )
    }

    private class Found(val text: String, var count: Int, var price: Double?, var kilograms: Double? = null)

    /**
     * Lowers [product]'s price by the discount printed as [text]. A discount larger than the price is
     * misread: a crossed zero read as an 8 ("-8,10" for "-0,10") is tried, else it is left out, and the
     * receipt's total shows something is amiss.
     */
    private fun discount(product: Found, text: String) {
        val price = product.price ?: return
        val off = listOfNotNull(price(text), price(text.replace('8', '0'))).map(::abs).firstOrNull { it < price } ?: return
        product.price = price - off
    }

    private class Quantity(val count: Int, val unit: Double?)

    private class Weight(val kilograms: Double, val perKilogram: Double?)

    private fun letters(text: String) = text.count { it.isLetter() }

    private fun price(text: String): Double? = text.replace(" ", "").replace(',', '.').toDoubleOrNull()

    private fun List<Float>.median(): Float? = if (isEmpty()) null else sorted()[size / 2]

    private const val MAX_QUANTITY = 99

    /** How far up the photos before an overlap is looked for, in rows. */
    private const val OVERLAP_WINDOW = 15

    /** Rows cut by a photo's edge, at the end of one or the start of the next, that may not match. */
    private const val SEAM_SLACK = 2

    /** A single shared row is taken for an overlap only if it has this many letters and digits. */
    private const val MIN_SEAM_LETTERS = 6

    /** An amount at the end of a row, maybe followed by a currency and a VAT code ("1,29 €", "0,99 A"). */
    private val PRICE_AT_END = Regex("""(?:^|\s)(-?\d{1,4}[.,]\s?\d{2})\s*(?:€|EUR|E)?\s*(?:[A-Z]{1,2}|\*)?\s*$""")

    /** "2 x 0,89", "2 PZ X 0.89", "3*1,20"; recognizers sometimes read "0, 89". */
    private val QUANTITY = Regex("""^(\d{1,3})\s*(?:PZ\.?|N\.?)?\s*[X×*]\s*(\d{1,4}[.,]\s?\d{2})""")

    /** "0,846 kg x 1,52 €/kg": the weight of a product sold by weight. */
    private val WEIGHT = Regex("""\d[.,]\d{3}\s*KG|[€E](?:UR)?\s*/\s*KG|\bKG\s*[X×*]""")
    private val KILOGRAMS = Regex("""(\d{1,3}[.,]\d{3})\s*KG""")
    private val PER_KILOGRAM = Regex("""(\d{1,4}[.,]\d{2})\s*(?:€|EUR|E)?\s*/\s*KG""")

    /** The row of the total to pay, after which no products follow. */
    private val TOTAL = Regex("""^TOTAL[EI]?\b(?!.*\bIVA\b)""")

    /** A VAT column just before the price: "4%", "10 %", "22,00%". */
    private val VAT = Regex("""\s\d{1,2}(?:[.,]\d{1,2})?\s?%\s*$""")

    /** Rows after which no products follow. */
    private val END = Regex("""^(?:SUB\s?)?TOTAL[EI]?\b|^IMPORTO\b|^PAGAMENTO\b|^PAGATO\b|^CONTANT[EI]\b|^RESTO\b|^SUMME\b""")

    /** Rows that carry an amount but are not products. */
    private val SKIP = Regex(
        """\bP\.?\s?IVA\b|\bDESCRIZIONE\b|\bPREZZO\b|\bDI CUI\b|\bIVA\b\s*$|\bC\.?F\.?\b|\bTEL\b|\bDOCUMENTO\b|\bSCONTRINO\b|\bCASSA\b|\bOPERATORE\b|""" +
            """\bCASSIER[EA]\b|\bARTICOLI\b|\bPEZZI\b|\bPUNTI\b|\bSALDO\b|\bBANCOMAT\b|\bVISA\b|\bMASTERCARD\b|""" +
            """\bCREDITO\b|\bDEBITO\b|\bARROTONDAMENTO\b|\bBUONO\b|\bRISPARMI""",
    )

    /** Discounts, which lower another line's price and are not products of their own. */
    private val DISCOUNT = Regex("""\bSCONTO\b|\bSC\.\b|\bRIBASSO\b|\bPROMO\b|\bRIDUZIONE\b""")
}

/**
 * A first guess at the food a receipt line is, for lines the app has never seen: the description
 * without sizes, codes and abbreviation dots, in sentence case. "LATTE P.S. ESSELUNGA 1L" becomes
 * "Latte esselunga". The user corrects it, and the app remembers.
 */
fun guessName(receiptText: String): String {
    val words = receiptText
        // A size the reader ran into the word before it: "ZUCCHERO1KG".
        .replace(Regex("""(?<=\p{L})(?=\d)"""), " ")
        .replace(Regex("""\d+(?:[.,]\d+)?\s?%"""), " ")
        .replace(Regex("""(?i)\b\d*[.,]?\d+\s?[x×*]\s?\d+(?:[.,]\d+)?\s?(?:kg|gr?|lt?|ml|cl)?\b"""), " ")
        .replace(Regex("""(?i)\b\d+(?:[.,]\d+)?\s?(?:kg|gr?|lt?|ml|cl|pz|cps|bt|conf)\b"""), " ")
        .replace(Regex("""[^\p{L}\s']"""), " ")
        .split(Regex("\\s+"))
        .filter { word -> word.count { it.isLetter() } >= 2 }
    val text = words.joinToString(" ").lowercase()
    return text.replaceFirstChar { it.uppercase() }
}

/** Whether a header row names the shop: words, not an address, a tax number, a phone or the receipt's own title. */
private fun namesShop(row: String): Boolean {
    val upper = row.uppercase()
    return row.count { it.isLetter() } >= 3 && row.count { it.isDigit() } <= 2 && !NOT_SHOP.containsMatchIn(upper)
}

/** A shop's name as printed, without its company form, in title case: "COOP ALLEANZA 3.0 SOC. COOP." is "Coop Alleanza". */
private fun tidyShop(row: String): String? {
    val words = row.replace(COMPANY, " ").replace(Regex("""[^\p{L}\s'&]"""), " ").split(Regex("\\s+"))
        .filter { word -> word.count { it.isLetter() } >= 2 }
    return words.joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }.takeIf { it.isNotBlank() }
}

/** Header rows that do not name the shop. */
private val NOT_SHOP = Regex(
    """\bVIA\b|\bV\.LE\b|\bVIALE\b|\bPIAZZA\b|\bP\.ZZA\b|\bCORSO\b|\bLOC\.|\bP\.?\s?IVA\b|\bPARTITA\b|\bC\.?F\.?\b|""" +
        """\bTEL\b|\bFAX\b|\bDOCUMENTO\b|\bCOMMERCIALE\b|\bSCONTRINO\b|\bFISCALE\b|\bVENDITA\b|\bDESCRIZIONE\b|""" +
        """\bPREZZO\b|\bEURO\b|\bBENVENUT|\bGRAZIE\b|\bWWW\b|\bCASSA\b|\bCAP\b""",
)

/** The company forms a shop's name is printed with. */
private val COMPANY = Regex("""(?i)\bS\.?\s?P\.?\s?A\.?(?!\p{L})|\bS\.?\s?R\.?\s?L\.?(?!\p{L})|\bSOC(?:IETA'?)?\.?\s?COOP(?:ERATIVA)?\.?|\bS\.?\s?C\.?\s?A\.?\s?R\.?\s?L\.?(?!\p{L})|\bS\.?\s?N\.?\s?C\.?(?!\p{L})|\d+[.,]\d+""")

