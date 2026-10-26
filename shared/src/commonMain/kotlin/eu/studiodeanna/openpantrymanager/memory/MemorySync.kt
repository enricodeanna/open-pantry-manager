package eu.studiodeanna.openpantrymanager.memory

import eu.studiodeanna.openpantrymanager.model.compareStamps
import eu.studiodeanna.openpantrymanager.voice.VoiceAction

/**
 * A thing the app has learned, the unit two copies of the memory are merged by: all it knows of a
 * food, where anything went from a space, what a receipt line is, or what a phrase said to voice
 * commands means. Each has a key: "food:" and the food's name key, "moves:" and the space's id,
 * "line:" and the line's receipt key, "phrase:", the language's code, ":" and the phrase.
 */
internal sealed interface Learned {
    data class OfFood(val food: Food) : Learned
    data class Moves(val to: Map<String, Double>) : Learned
    data class Line(val food: String?, val pack: Int?, val skipped: Boolean) : Learned
    data class Phrase(val means: VoiceAction) : Learned
}

private const val FOOD = "food:"
private const val MOVES = "moves:"
private const val LINE = "line:"
private const val PHRASE = "phrase:"

/** Everything learned, by key. */
internal fun Memory.learned(): Map<String, Learned> = buildMap {
    foods.forEach { (key, food) -> put(FOOD + key, Learned.OfFood(food)) }
    moves.forEach { (space, to) -> put(MOVES + space, Learned.Moves(to)) }
    (receipt.keys + packs.keys + skipped).forEach { line ->
        put(LINE + line, Learned.Line(receipt[line], packs[line], line in skipped))
    }
    phrases.forEach { (language, meanings) ->
        meanings.forEach { (phrase, action) -> put("$PHRASE$language:$phrase", Learned.Phrase(action)) }
    }
}

private fun memoryOf(learned: Map<String, Learned>, stamps: Map<String, Stamp>): Memory {
    val foods = HashMap<String, Food>()
    val moves = HashMap<String, Map<String, Double>>()
    val receipt = HashMap<String, String>()
    val packs = HashMap<String, Int>()
    val skipped = HashSet<String>()
    val phrases = HashMap<String, MutableMap<String, VoiceAction>>()
    for ((key, value) in learned) {
        when (value) {
            is Learned.OfFood -> foods[key.removePrefix(FOOD)] = value.food
            is Learned.Moves -> moves[key.removePrefix(MOVES)] = value.to
            is Learned.Line -> {
                val line = key.removePrefix(LINE)
                value.food?.let { receipt[line] = it }
                value.pack?.let { packs[line] = it }
                if (value.skipped) skipped += line
            }
            is Learned.Phrase -> {
                val (language, phrase) = key.removePrefix(PHRASE).split(':', limit = 2)
                phrases.getOrPut(language) { HashMap() }[phrase] = value.means
            }
        }
    }
    return Memory(foods = foods, moves = moves, receipt = receipt, packs = packs, skipped = skipped, phrases = phrases, stamps = stamps)
}

/**
 * Two copies of the memory as one: of each learned thing, the most recent change wins, as items and
 * spaces merge. The result does not depend on the order of the two, and merging again changes nothing.
 */
fun Memory.merge(other: Memory): Memory {
    val mine = learned()
    val theirs = other.learned()
    val learned = HashMap<String, Learned>()
    val stamps = HashMap<String, Stamp>()
    for (key in mine.keys + theirs.keys + this.stamps.keys + other.stamps.keys) {
        val a = this.stamps[key]
        val b = other.stamps[key]
        val order = compareStamps(a?.at ?: 0, a?.by.orEmpty(), b?.at ?: 0, b?.by.orEmpty())
        // The same stamp should mean the same change; for things learned before stamping, any fixed rule keeps merge symmetric.
        val fromMine = if (order != 0) order > 0 else mine[key].toString() >= theirs[key].toString()
        val (value, stamp) = if (fromMine) mine[key] to a else theirs[key] to b
        value?.let { learned[key] = it }
        stamp?.let { stamps[key] = it }
    }
    return memoryOf(learned, stamps)
}

/** [target], which this memory became on this device: the things that differ are stamped [stamp]. */
fun Memory.changedTo(target: Memory, stamp: Stamp): Memory {
    val changed = differencesFrom(target).keys
    if (changed.isEmpty()) return this
    return target.copy(stamps = stamps + changed.associateWith { stamp })
}

/** The things learned differently in [other], as this memory has them: null where it has nothing. */
internal fun Memory.differencesFrom(other: Memory): Map<String, Learned?> {
    val mine = learned()
    val theirs = other.learned()
    return (mine.keys + theirs.keys).filter { mine[it] != theirs[it] }.associateWith { mine[it] }
}

/** This memory with [things] put back as they were (null: unknown again), stamped [stamp]: an undo. */
internal fun Memory.withLearned(things: Map<String, Learned?>, stamp: Stamp): Memory {
    val learned = learned().toMutableMap()
    things.forEach { (key, value) -> if (value == null) learned.remove(key) else learned[key] = value }
    return memoryOf(learned, stamps + things.keys.associateWith { stamp })
}

/**
 * [changes] (each thing learned: before, after) taken back, where this memory still knows what they
 * made it; what was learned again since stays. Stamped [stamp].
 */
internal fun Memory.reverted(changes: Map<String, Pair<Learned?, Learned?>>, stamp: Stamp): Memory {
    val current = learned()
    val back = changes.filter { (key, change) -> current[key] == change.second }.mapValues { it.value.first }
    return if (back.isEmpty()) this else withLearned(back, stamp)
}

/** A stamp for a change on [device] at [now], never before a change this memory has already seen. */
fun Memory.nextStamp(now: Long, device: String): Stamp =
    Stamp(maxOf(now, (stamps.values.maxOfOrNull { it.at } ?: 0) + 1), device)

/**
 * This memory with spaces given new ids ([ids], old to new), as when a phone's pantry joins a shared one
 * and its spaces become the shared spaces of the same name. Habits of two spaces that become one add up.
 */
fun Memory.withSpaces(ids: Map<String, String>): Memory {
    if (ids.isEmpty()) return this
    fun Map<String, Double>.renamed(): Map<String, Double> =
        entries.groupBy({ ids[it.key] ?: it.key }, { it.value }).mapValues { it.value.sum() }
    fun Map<String, Map<String, Double>>.renamedMoves(): Map<String, Map<String, Double>> =
        entries.groupBy({ ids[it.key] ?: it.key }, { it.value.renamed() })
            .mapValues { (_, maps) -> maps.reduce { a, b -> a.sumWith(b) } }
    return copy(
        foods = foods.mapValues { (_, food) -> food.copy(places = food.places.renamed(), moves = food.moves.renamedMoves()) },
        moves = moves.renamedMoves(),
    )
}

/**
 * This memory and [other], learned apart on two phones, as one, when one phone's pantry joins the
 * other's: a food's habits, and where things went from each space, add up; of everything else the
 * more recent wins.
 */
fun Memory.combinedWith(other: Memory): Memory {
    val merged = merge(other)
    val foods = merged.foods.mapValues { (key, food) ->
        val a = this.foods[key]
        val b = other.foods[key]
        if (a == null || b == null) food else combine(a, b)
    }
    val moves = merged.moves.mapValues { (space, to) ->
        val a = this.moves[space]
        val b = other.moves[space]
        if (a == null || b == null) to else a.sumWith(b)
    }
    return merged.copy(foods = foods, moves = moves)
}

private fun combine(a: Food, b: Food): Food {
    val (newer, older) = if (a.last >= b.last) a to b else b to a
    return newer.copy(
        count = a.count + b.count,
        places = a.places.sumWith(b.places),
        moves = (a.moves.keys + b.moves.keys).associateWith { a.moves[it].orEmpty().sumWith(b.moves[it].orEmpty()) },
        keeps = (older.keeps + newer.keeps).takeLast(KEEPS_COMBINED),
        keepsDays = typicalKeep((older.keeps + newer.keeps).takeLast(KEEPS_COMBINED))
            ?: listOfNotNull(a.keepsDays, b.keepsDays).takeIf { it.isNotEmpty() }?.average()?.toInt(),
        sublist = newer.sublist ?: older.sublist,
        shoppingName = newer.shoppingName ?: older.shoppingName,
        boxed = newer.boxed ?: older.boxed,
    )
}

/** Weights by key added up. */
private fun Map<String, Double>.sumWith(other: Map<String, Double>): Map<String, Double> =
    (keys + other.keys).associateWith { (this[it] ?: 0.0) + (other[it] ?: 0.0) }

/** Keeping times kept when two phones' are combined, as many as one phone keeps. */
private const val KEEPS_COMBINED = 5
