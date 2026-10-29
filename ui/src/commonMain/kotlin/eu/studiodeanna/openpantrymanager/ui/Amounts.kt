package eu.studiodeanna.openpantrymanager.ui

import eu.studiodeanna.openpantrymanager.costs.Measure
import eu.studiodeanna.openpantrymanager.costs.MeasureUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/** A number as this language writes it, with at most [decimals] decimals and none that are zero: "1,5", "500". */
fun Strings.number(value: Double, decimals: Int = 2): String {
    var scale = 1L
    repeat(decimals) { scale *= 10 }
    val scaled = (abs(value) * scale).roundToLong()
    val whole = scaled / scale
    val fraction = (scaled % scale).toString().padStart(decimals, '0').trimEnd('0')
    return (if (value < 0 && scaled > 0) "-" else "") + whole + (if (fraction.isEmpty()) "" else decimal + fraction)
}

/** "500 g", "1,5 kg", "750 ml", "1 l": grams and millilitres, in thousands from a thousand up. */
fun Strings.measure(amount: Measure): String {
    val big = abs(amount.value) >= 1000
    val unit = when (amount.unit) {
        MeasureUnit.Gram -> if (big) "kg" else "g"
        MeasureUnit.Millilitre -> if (big) "l" else "ml"
    }
    return number(if (big) amount.value / 1000 else amount.value, if (big) 3 else 1) + " " + unit
}

/** The short name of a unit: "g", "ml". */
fun MeasureUnit.symbol(): String = when (this) {
    MeasureUnit.Gram -> "g"
    MeasureUnit.Millilitre -> "ml"
}

/** A number as typed, with a comma or a point: "1,99", "1.5"; null when it is none. */
fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }

/** An amount of money as typed, in cents: "1,99" is 199. */
fun parseMoney(text: String): Long? = parseNumber(text.replace("€", ""))?.let { (it * 100).roundToLong() }

/** Cents as typed back into a field: "1,99". */
fun Strings.moneyField(cents: Long): String = (cents / 100).toString() + decimal + (cents % 100).toString().padStart(2, '0')
