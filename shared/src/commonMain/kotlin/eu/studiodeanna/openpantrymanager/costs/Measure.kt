package eu.studiodeanna.openpantrymanager.costs

import kotlin.math.roundToLong
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What food is measured in, besides pieces: by weight, in grams, or by volume, in millilitres. */
@Serializable
enum class MeasureUnit {
    @SerialName("g") Gram,
    @SerialName("ml") Millilitre,
}

/** An amount of food: a package's content ("500 g"), what is left of it, or what was used or weighed. */
@Serializable
data class Measure(val value: Double, val unit: MeasureUnit) {
    operator fun times(count: Int) = Measure(value * count, unit)

    /** In kilograms or litres. */
    val thousands: Double get() = value / 1000
}

/**
 * The size of a package written in a product's description, as receipts and labels print it: "500G",
 * "1,5 LT", "YOGURT 4X125G" (each pot: 125 g), "ZUCCHERO1KG"; null when it names none.
 */
fun sizeIn(text: String): Measure? {
    val match = SIZE.findAll(text.uppercase()).lastOrNull() ?: return null
    val number = match.groupValues[1].replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: return null
    return when (match.groupValues[2]) {
        "KG" -> Measure(number * 1000, MeasureUnit.Gram)
        "G", "GR" -> Measure(number, MeasureUnit.Gram)
        "L", "LT" -> Measure(number * 1000, MeasureUnit.Millilitre)
        "CL" -> Measure(number * 10, MeasureUnit.Millilitre)
        "ML" -> Measure(number, MeasureUnit.Millilitre)
        else -> null
    }?.let { Measure((it.value * 100).roundToLong() / 100.0, it.unit) }
}

/**
 * "500G", "1,5 LT", "6X1,5L": a number, maybe after a count and an x, then a unit that ends the word. A
 * word may run into it, as the reader joins them ("ZUCCHERO1KG").
 */
private val SIZE = Regex("""(?<![\d.,])(?:\d{1,2}\s?[X×*]\s?)?(\d+(?:[.,]\d+)?)\s?(KG|GR|G|LT|L|CL|ML)(?!\p{L})""")
