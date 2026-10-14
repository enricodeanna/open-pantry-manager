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
        const val FORMAT = 1
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

/** How the items of a space are listed. */
@Serializable
enum class ItemOrder { Name, Expiry }

/**
 * Live items by space, each space's in [order]. The null key holds items whose space is gone, which
 * happens when a space is deleted on one device while food is put in it on another.
 */
fun PantryDocument.itemsBySpace(order: ItemOrder = ItemOrder.Name): Map<String?, List<PantryItem>> {
    val live = visibleSpaces().mapTo(HashSet()) { it.id }
    val byName = compareBy<PantryItem>({ nameKey(it.name) }, { it.expires }, { it.id })
    val comparator = when (order) {
        ItemOrder.Name -> byName
        // Items without a date go last; among the rest, the soonest first.
        ItemOrder.Expiry -> compareBy<PantryItem>({ it.expires == null }, { it.expires }).then(byName)
    }
    return liveItems().sortedWith(comparator).groupBy { if (it.space in live) it.space else null }
}

fun PantryDocument.merge(other: PantryDocument): PantryDocument = PantryDocument(
    format = maxOf(format, other.format),
    spaces = mergeStamped(spaces, other.spaces),
    items = mergeStamped(items, other.items),
)

fun PantryDocument.pruned(now: Long): PantryDocument =
    copy(spaces = spaces.prunedTombstones(now), items = items.prunedTombstones(now))
