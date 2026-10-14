package eu.studiodeanna.openpantrymanager.checklist

import eu.studiodeanna.openpantrymanager.model.Stamped
import kotlinx.serialization.Serializable

/**
 * A list of Open Check Lists, exactly as that app stores it in its file: the shopping list is one,
 * and both apps edit the same file. Fields and their meaning must stay as Open Check Lists has them;
 * a file of a newer [FORMAT] is refused rather than damaged.
 *
 * Every section and item carries a stamp (`at`, `by`) of its last change, and deletions are kept as
 * tombstones, so two copies edited apart can always be merged with [merge].
 */
@Serializable
data class ListDocument(
    val format: Int = FORMAT,
    val title: String = "",
    val titleAt: Long = 0,
    val titleBy: String = "",
    val sections: List<Section> = emptyList(),
    val items: List<Item> = emptyList(),
) {
    companion object {
        const val FORMAT = 1
    }
}

/** A section of a list; the pantry calls them sublists, as in "Dairy" or "Greengrocer". */
@Serializable
data class Section(
    override val id: String,
    val name: String,
    val pos: Double,
    override val at: Long,
    override val by: String,
    override val deleted: Boolean = false,
) : Stamped

@Serializable
data class Item(
    override val id: String,
    val text: String,
    /** Owning section, or null for items that sit above every section. */
    val section: String? = null,
    val done: Boolean = false,
    val pos: Double,
    override val at: Long,
    override val by: String,
    override val deleted: Boolean = false,
) : Stamped

/** Latest stamp time anywhere in the document; new edits are stamped after it. */
fun ListDocument.latestStamp(): Long =
    maxOf(titleAt, sections.maxOfOrNull { it.at } ?: 0, items.maxOfOrNull { it.at } ?: 0)

/** Live sections in display order. */
fun ListDocument.visibleSections(): List<Section> =
    sections.filterNot { it.deleted }.sortedWith(compareBy({ it.pos }, { it.id }))

/**
 * Live items grouped for display: the null key holds items without a (live) section, which happens
 * when a section is deleted on one device while an item is added to it on another.
 */
fun ListDocument.itemsBySection(): Map<String?, List<Item>> {
    val live = visibleSections().mapTo(HashSet()) { it.id }
    return items.filterNot { it.deleted }
        .sortedWith(compareBy({ it.pos }, { it.id }))
        .groupBy { if (it.section in live) it.section else null }
}
