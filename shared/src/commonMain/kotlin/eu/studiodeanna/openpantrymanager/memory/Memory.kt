package eu.studiodeanna.openpantrymanager.memory

import eu.studiodeanna.openpantrymanager.model.nameKey
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * What the app has learned from the user: where each food usually goes, where it is usually moved to
 * from each space, how long it usually keeps, what it is called on the shopping list and in which
 * sublist, and what the lines of shop receipts mean. A shared pantry shares it too.
 *
 * Habits are weights that grow by one each time and fade a little whenever something else happens to
 * the same food, so a new habit takes over after being repeated a few times.
 *
 * [stamps] say when each learned thing last changed, and on which device, so that two copies can be
 * merged (see MemorySync.kt).
 */
@Serializable
data class Memory(
    /** By [nameKey] of the food's name. */
    val foods: Map<String, Food> = emptyMap(),
    /** Space → space moves of any food, used for foods not yet moved from that space. */
    val moves: Map<String, Map<String, Double>> = emptyMap(),
    /** A receipt line's [receiptKey] → the [nameKey] of the food it is. */
    val receipt: Map<String, String> = emptyMap(),
    /** A receipt line's [receiptKey] → how many items one of it is, for packs ("WATER 6X1,5L" is 6 bottles). */
    val packs: Map<String, Int> = emptyMap(),
    /** Receipt lines the user left out: not food, or not for the pantry (a bag, a deposit). */
    val skipped: Set<String> = emptySet(),
    /** By learned thing (see [entries]): its last change. Things learned before stamping have none. */
    val stamps: Map<String, Stamp> = emptyMap(),
)

/** When something changed, and on which device. */
@Serializable
data class Stamp(val at: Long, val by: String)

@Serializable
data class Food(
    /** As the user last wrote it. */
    val name: String,
    /** Times it was put in the pantry. */
    val count: Int = 0,
    val last: Long = 0,
    /** Space id → weight. */
    val places: Map<String, Double> = emptyMap(),
    /** From space id → (to space id → weight). */
    val moves: Map<String, Map<String, Double>> = emptyMap(),
    /** Days it usually keeps from the day it is bought to its expiry day. */
    val keepsDays: Int? = null,
    /** The name of the shopping-list sublist it went on last time; "" for the list's top, outside sublists. */
    val sublist: String? = null,
    /** What it is called on the shopping list, when the user named it otherwise there ("Latte" for "Latte p.s."). */
    val shoppingName: String? = null,
)

/** How quickly an old habit fades each time the same food is handled differently. */
private const val FADE = 0.8

private fun Map<String, Double>.reinforced(key: String): Map<String, Double> =
    mapValues { it.value * FADE }.let { it + (key to (it[key] ?: 0.0) + 1.0) }

private fun Map<String, Double>.best(allowed: Set<String>, except: String? = null): String? =
    filterKeys { it in allowed && it != except }.maxByOrNull { it.value }?.key

fun Memory.food(name: String): Food? = foods[nameKey(name)]

private fun Memory.withFood(name: String, change: (Food) -> Food): Memory {
    val key = nameKey(name)
    if (key.isEmpty()) return this
    val food = foods[key] ?: Food(name.trim())
    return copy(foods = foods + (key to change(food).copy(name = name.trim())))
}

/**
 * Remembers that [name] was put in [space]. With an [expires] day, it also learns how long that food
 * keeps from [today].
 */
fun Memory.placed(name: String, space: String, now: Long, today: LocalDate, expires: LocalDate? = null): Memory =
    withFood(name) { food ->
        val days = expires?.let { today.daysUntil(it) }?.takeIf { it in 0..MAX_KEEP_DAYS }
        food.copy(
            count = food.count + 1,
            last = now,
            places = food.places.reinforced(space),
            keepsDays = when {
                days == null -> food.keepsDays
                food.keepsDays == null -> days
                else -> ((food.keepsDays + days) / 2.0).roundToInt()
            },
        )
    }

/** Remembers that [name] was moved from one space to another. */
fun Memory.moved(name: String, from: String, to: String): Memory =
    withFood(name) { food ->
        food.copy(moves = food.moves + (from to (food.moves[from] ?: emptyMap()).reinforced(to)))
    }.copy(moves = moves + (from to (moves[from] ?: emptyMap()).reinforced(to)))

/** The space [name] usually goes in, among [spaces]; null if the app has not seen it yet. */
fun Memory.placeFor(name: String, spaces: Set<String>): String? = food(name)?.places?.best(spaces)

/**
 * Where [name] probably goes when moved out of [from]: where that food went from there before, else
 * where anything went from there, else where that food usually is. Null when there is no clue.
 */
fun Memory.moveFor(name: String, from: String, spaces: Set<String>): String? {
    val food = food(name)
    return food?.moves?.get(from)?.best(spaces, except = from)
        ?: moves[from]?.best(spaces, except = from)
        ?: food?.places?.best(spaces, except = from)
}

/**
 * Known foods whose name [text] could be the start of, best first: the closest matches, then the
 * ones bought most often, then the most recent.
 */
fun Memory.complete(text: String, limit: Int = 6): List<Food> {
    val query = nameKey(text)
    if (query.isEmpty()) return emptyList()
    return foods.entries.mapNotNull { (key, food) ->
        val rank = when {
            key == query -> 0
            key.startsWith(query) -> 1
            key.split(' ').any { it.startsWith(query) } -> 2
            query.length >= 3 && query in key -> 3
            else -> return@mapNotNull null
        }
        rank to food
    }.sortedWith(compareBy({ it.first }, { -it.second.count }, { -it.second.last }))
        .take(limit)
        .map { it.second }
}

/**
 * Remembers that [name] went on the shopping list as [listName], in the sublist [sublist] ("" for none).
 */
fun Memory.wentOnList(name: String, listName: String, sublist: String): Memory = withFood(name) { food ->
    food.copy(sublist = sublist, shoppingName = listName.trim().takeIf { nameKey(it) != nameKey(name) })
}

/**
 * Learns that the receipt line [line] (as [receiptKey] gives it) is the food [name], [pack] items of
 * it for each one printed.
 */
fun Memory.receiptMeans(line: String, name: String, pack: Int = 1): Memory {
    val key = receiptKey(line)
    return copy(
        receipt = receipt + (key to nameKey(name)),
        packs = if (pack > 1) packs + (key to pack) else packs - key,
        skipped = skipped - key,
    )
}

/** Learns that the receipt line [line] is not for the pantry. */
fun Memory.receiptSkips(line: String): Memory = copy(skipped = skipped + receiptKey(line), receipt = receipt - receiptKey(line))

/**
 * What receipt lines are compared by: [nameKey] without numbers, so the same product with another
 * price, weight or quantity prefix reads as the same line.
 */
fun receiptKey(line: String): String =
    nameKey(line).split(' ').filter { word -> word.any { it.isLetter() } }.joinToString(" ")

/** Expiry days further away than this are taken for typing mistakes and not learned. */
private const val MAX_KEEP_DAYS = 3 * 365
