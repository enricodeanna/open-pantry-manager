package eu.studiodeanna.openpantrymanager.costs

import eu.studiodeanna.openpantrymanager.memory.Memory
import eu.studiodeanna.openpantrymanager.memory.food
import eu.studiodeanna.openpantrymanager.memory.groupOf
import eu.studiodeanna.openpantrymanager.model.nameKey
import kotlin.math.roundToLong
import kotlinx.datetime.LocalDate

/** What the costs screen adds purchases up by. */
enum class CostGrouping {
    /** Each food as the pantry calls it. */
    Product,

    /** Like products together ([groupOf]). */
    Group,

    /** The shopping list's sublist each food goes in; "" for none. */
    Sublist,

    /** The shop; "" where it is not known. */
    Shop,
}

/** What was spent on one product, group, sublist or shop: [purchases] of it, newest first. */
data class CostLine(val name: String, val purchases: List<Purchase>) {
    val cents: Long = purchases.sumOf { it.cents }
    val pieces: Int = purchases.sumOf { it.quantity }

    /** The price of one piece, on average. */
    val centsPerPiece: Long? = if (pieces > 0) (cents.toDouble() / pieces).roundToLong() else null

    /** The price of a kilogram or litre, on average, over the purchases whose amount is known; null if none is. */
    val centsPerThousand: Long? = run {
        val measured = purchases.filter { it.amount != null && it.amount.value > 0 }
        if (measured.isEmpty() || measured.map { it.amount!!.unit }.distinct().size != 1) return@run null
        (measured.sumOf { it.cents } / measured.sumOf { it.amount!!.thousands }).roundToLong()
    }

    /** What the prices above are of: by weight or volume, when known. */
    val unit: MeasureUnit? = purchases.firstNotNullOfOrNull { it.amount?.unit }?.takeIf { centsPerThousand != null }

    /**
     * How the latest price compares with the average of the earlier ones, as a fraction (+0.12: 12% more);
     * by the kilogram or litre where both are known, else by the piece. Null before two purchases.
     */
    val lastChange: Double? = run {
        if (purchases.size < 2) return@run null
        val latest = purchases.first()
        val earlier = CostLine(name, purchases.drop(1))
        val (now, before) = if (unit != null && latest.amount != null && earlier.centsPerThousand != null) {
            latest.cents / latest.amount.thousands to earlier.centsPerThousand.toDouble()
        } else {
            latest.cents.toDouble() / latest.quantity.coerceAtLeast(1) to (earlier.centsPerPiece ?: return@run null).toDouble()
        }
        if (before <= 0) null else now / before - 1
    }
}

/**
 * The purchases of [costs] made from [from] to [to] (either open), added up [by] product, group,
 * sublist or shop, the most spent on first. Groups and sublists are as [memory] knows them now, so a
 * product grouped today counts in its group for the past too.
 */
fun costLines(costs: CostsDocument, memory: Memory, by: CostGrouping, from: LocalDate? = null, to: LocalDate? = null): List<CostLine> {
    val purchases = costs.livePurchases()
        .filter { (from == null || it.day >= from) && (to == null || it.day <= to) }
        .sortedWith(compareByDescending<Purchase> { it.day }.thenByDescending { it.at })
    return purchases
        .groupBy { purchase -> nameKey(costName(purchase, memory, by)) }
        .map { (_, of) -> CostLine(costName(of.first(), memory, by), of) }
        .sortedWith(compareByDescending<CostLine> { it.cents }.thenBy { nameKey(it.name) })
}

/** What [purchase] counts under, [by] product, group, sublist or shop; "" for no sublist or shop. */
fun costName(purchase: Purchase, memory: Memory, by: CostGrouping): String = when (by) {
    CostGrouping.Product -> memory.food(purchase.food)?.name ?: purchase.food
    CostGrouping.Group -> memory.groupOf(purchase.food)
    CostGrouping.Sublist -> memory.food(purchase.food)?.sublist.orEmpty()
    CostGrouping.Shop -> purchase.shop.orEmpty()
}

/** Groups of like products already named, by the user or by the shopping list, to put another food in. */
fun knownGroups(costs: CostsDocument, memory: Memory): List<String> =
    costs.livePurchases().map { memory.groupOf(it.food) }.distinctBy(::nameKey).sortedBy(::nameKey)
