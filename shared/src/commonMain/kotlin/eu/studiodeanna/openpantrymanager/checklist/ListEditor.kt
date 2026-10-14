package eu.studiodeanna.openpantrymanager.checklist

import eu.studiodeanna.openpantrymanager.model.newId

/**
 * The few changes the pantry makes to a shopping list, stamped for this device as Open Check Lists
 * stamps its own. Stamps never go backwards relative to what the document already contains, so a
 * device with a slow clock still wins over changes it has already seen.
 */
class ListEditor(private val device: String, private val now: () -> Long) {
    private fun ListDocument.stamp(): Long = maxOf(now(), latestStamp() + 1)

    /** Adds a section at the end and returns the document and the new section's id. */
    fun addSection(doc: ListDocument, name: String): Pair<ListDocument, String> {
        val pos = (doc.visibleSections().maxOfOrNull { it.pos } ?: 0.0) + 1.0
        val section = Section(newId(), name.trim(), pos, doc.stamp(), device)
        return doc.copy(sections = doc.sections + section) to section.id
    }

    /** Adds an item at the end of [section], or of the items outside sections when null. */
    fun addItem(doc: ListDocument, section: String?, text: String): ListDocument {
        val siblings = doc.itemsBySection()[section].orEmpty()
        val pos = (siblings.maxOfOrNull { it.pos } ?: 0.0) + 1.0
        val item = Item(newId(), text.trim(), section, pos = pos, at = doc.stamp(), by = device)
        return doc.copy(items = doc.items + item)
    }

    /**
     * Puts [text] back on the list in [section]: a checked item of that name there is unchecked
     * (lists reused for every shop keep their items), otherwise a new item is added.
     */
    fun putOnList(doc: ListDocument, section: String?, text: String): ListDocument {
        val checked = doc.itemsBySection()[section].orEmpty().firstOrNull { it.done && sameText(it.text, text) }
            ?: return addItem(doc, section, text)
        return setDone(doc, checked.id, false)
    }

    /**
     * Takes [change] back as an edit of its own: each section and item it touched returns to how it was
     * (one it created is deleted), unless it has been changed again since, which is left as it is.
     */
    fun revert(doc: ListDocument, change: ListChange): ListDocument {
        val at = doc.stamp()
        val sections = change.sections.associateBy { (_, new) -> new.id }
        val items = change.items.associateBy { (_, new) -> new.id }
        return doc.copy(
            sections = doc.sections.map { current ->
                val (old, new) = sections[current.id] ?: return@map current
                if (!current.sameAs(new)) current else (old ?: new.copy(deleted = true)).copy(at = at, by = device)
            },
            items = doc.items.map { current ->
                val (old, new) = items[current.id] ?: return@map current
                if (!current.sameAs(new)) current else (old ?: new.copy(deleted = true)).copy(at = at, by = device)
            },
        )
    }

    fun setDone(doc: ListDocument, id: String, done: Boolean): ListDocument {
        val at = doc.stamp()
        return doc.copy(items = doc.items.map { if (it.id == id) it.copy(done = done, at = at, by = device) else it })
    }
}

/** An unchecked item of the list that is [text], ignoring case and spacing; null if none. */
fun ListDocument.pending(text: String): Item? =
    items.firstOrNull { !it.deleted && !it.done && sameText(it.text, text) }

private fun sameText(a: String, b: String): Boolean =
    a.trim().lowercase().replace(Regex("\\s+"), " ") == b.trim().lowercase().replace(Regex("\\s+"), " ")
