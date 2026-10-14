package eu.studiodeanna.openpantrymanager.checklist

import eu.studiodeanna.openpantrymanager.model.compareStamps
import eu.studiodeanna.openpantrymanager.model.mergeStamped
import eu.studiodeanna.openpantrymanager.model.prunedTombstones

/**
 * Combines two copies of a list, by the same rules as Open Check Lists: per section and per item the
 * most recent change wins, and the title likewise.
 */
fun merge(a: ListDocument, b: ListDocument): ListDocument {
    val order = compareStamps(a.titleAt, a.titleBy, b.titleAt, b.titleBy)
    // Equal stamps with different titles only happen with hand-made files; any fixed rule keeps merge symmetric.
    val titleFromA = if (order != 0) order > 0 else a.title >= b.title
    val title = if (titleFromA) a else b
    return ListDocument(
        format = maxOf(a.format, b.format),
        title = title.title,
        titleAt = title.titleAt,
        titleBy = title.titleBy,
        sections = mergeStamped(a.sections, b.sections),
        items = mergeStamped(a.items, b.items),
    )
}

/** The canonical form of a document: what [merge] would produce, used to compare copies. */
fun ListDocument.normalized(): ListDocument = merge(this, this)

/** Drops old tombstones, as Open Check Lists does before each sync. */
fun ListDocument.pruned(now: Long): ListDocument =
    copy(sections = sections.prunedTombstones(now), items = items.prunedTombstones(now))
