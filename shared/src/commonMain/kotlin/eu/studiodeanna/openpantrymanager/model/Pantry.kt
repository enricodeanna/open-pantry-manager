package eu.studiodeanna.openpantrymanager.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Everything in the pantry: the storage spaces ("Kitchen cabinet", "Basement fridge") and the food
 * in them. Stamped and merged like an Open Check Lists list, so that the pantry can later be shared
 * between devices with the same rules.
 */
@Serializable
data class PantryDocument(
    val format: Int = FORMAT,
    val spaces: List<Space> = emptyList(),
    val items: List<PantryItem> = emptyList(),
) {
    companion object {
        /** 2: items have the day they were bought. An older app refuses the file rather than dropping it. */
        const val FORMAT = 2
    }
}

@Serializable
data class Space(
    override val id: String,
    val name: String,
    val pos: Double,
    override val at: Long,
    override val by: String,
    override val deleted: Boolean = false,
) : Stamped

/**
 * Some of one food in one space. The same food with another expiry day is another item, so each
 * batch keeps its own date. [added] is when it first went into the pantry.
 */
@Serializable
data class PantryItem(
    override val id: String,
    val name: String,
    val space: String,
    val quantity: Int = 1,
    val expires: LocalDate? = null,
    /** The day it was bought, if known; the day it was put in the pantry, unless the user said otherwise. */
    val bought: LocalDate? = null,
    val added: Long,
    override val at: Long,
    override val by: String,
    override val deleted: Boolean = false,
) : Stamped

fun PantryDocument.latestStamp(): Long =
    maxOf(spaces.maxOfOrNull { it.at } ?: 0, items.maxOfOrNull { it.at } ?: 0)

/** Live spaces in display order. */
fun PantryDocument.visibleSpaces(): List<Space> =
    spaces.filterNot { it.deleted }.sortedWith(compareBy({ it.pos }, { it.id }))

fun PantryDocument.liveItems(): List<PantryItem> = items.filterNot { it.deleted }

fun PantryDocument.item(id: String): PantryItem? = items.firstOrNull { it.id == id && !it.deleted }

fun PantryDocument.space(id: String?): Space? = spaces.firstOrNull { it.id == id && !it.deleted }

/**
 * Live items by space, each space's by expiry day: the soonest first, items without a day last, and
 * by name where the day is the same. The null key holds items whose space is gone, which happens when
 * a space is deleted on one device while food is put in it on another.
 */
fun PantryDocument.itemsBySpace(): Map<String?, List<PantryItem>> {
    val live = visibleSpaces().mapTo(HashSet()) { it.id }
    val order = compareBy<PantryItem>({ it.expires == null }, { it.expires }, { nameKey(it.name) }, { it.id })
    return liveItems().sortedWith(order).groupBy { if (it.space in live) it.space else null }
}

fun PantryDocument.merge(other: PantryDocument): PantryDocument = PantryDocument(
    format = maxOf(format, other.format),
    spaces = mergeStamped(spaces, other.spaces),
    items = mergeStamped(items, other.items),
)

fun PantryDocument.pruned(now: Long): PantryDocument =
    copy(spaces = spaces.prunedTombstones(now), items = items.prunedTombstones(now))
