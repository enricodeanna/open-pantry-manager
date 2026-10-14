package eu.studiodeanna.openpantrymanager.model

import kotlinx.datetime.LocalDate

/**
 * Applies user edits to the pantry, stamping each change for this device. Stamps never go backwards
 * relative to what the document already contains, so a device with a slow clock still wins over
 * changes it has already seen.
 */
class PantryEditor(private val device: String, private val now: () -> Long) {
    private fun PantryDocument.stamp(): Long = maxOf(now(), latestStamp() + 1)

    fun addSpace(doc: PantryDocument, name: String): Pair<PantryDocument, String> {
        val pos = (doc.visibleSpaces().maxOfOrNull { it.pos } ?: 0.0) + 1.0
        val space = Space(newId(), name.trim(), pos, doc.stamp(), device)
        return doc.copy(spaces = doc.spaces + space) to space.id
    }

    fun renameSpace(doc: PantryDocument, id: String, name: String): PantryDocument {
        val at = doc.stamp()
        return doc.copy(spaces = doc.spaces.map { if (it.id == id) it.copy(name = name.trim(), at = at, by = device) else it })
    }

    /** Moves a space one place up ([offset] = -1) or down (+1). */
    fun moveSpace(doc: PantryDocument, id: String, offset: Int): PantryDocument {
        val ordered = doc.visibleSpaces()
        val index = ordered.indexOfFirst { it.id == id }
        val target = index + offset
        if (index < 0 || target !in ordered.indices) return doc
        val rest = ordered.filterIndexed { i, _ -> i != index }
        val before = rest.getOrNull(target - 1)?.pos
        val after = rest.getOrNull(target)?.pos
        val pos = when {
            before == null && after == null -> 1.0
            before == null -> after!! - 1.0
            after == null -> before + 1.0
            else -> (before + after) / 2
        }
        val at = doc.stamp()
        return doc.copy(spaces = doc.spaces.map { if (it.id == id) it.copy(pos = pos, at = at, by = device) else it })
    }

    /** Deletes a space together with the food in it. */
    fun deleteSpace(doc: PantryDocument, id: String): PantryDocument {
        val at = doc.stamp()
        return doc.copy(
            spaces = doc.spaces.map { if (it.id == id) it.copy(deleted = true, at = at, by = device) else it },
            items = doc.items.map { if (it.space == id && !it.deleted) it.copy(deleted = true, at = at, by = device) else it },
        )
    }

    /**
     * Puts [quantity] of [name] in [space]. If the space already holds that food with the same expiry
     * day, it is counted there instead of listed twice. Returns the document and the item's id.
     */
    fun addItem(
        doc: PantryDocument,
        name: String,
        space: String,
        quantity: Int = 1,
        expires: LocalDate? = null,
    ): Pair<PantryDocument, String> {
        val at = doc.stamp()
        val same = doc.sameFood(name, space, expires)
        if (same != null) {
            return doc.withItem(same.copy(quantity = same.quantity + quantity, at = at, by = device)) to same.id
        }
        val item = PantryItem(newId(), name.trim(), space, quantity.coerceAtLeast(1), expires, added = now(), at = at, by = device)
        return doc.copy(items = doc.items + item) to item.id
    }

    /** Changes an item's name, quantity and expiry day; where it is changes with [moveItem]. */
    fun editItem(doc: PantryDocument, id: String, name: String, quantity: Int, expires: LocalDate?): PantryDocument {
        val item = doc.item(id) ?: return doc
        return doc.withItem(item.copy(name = name.trim(), quantity = quantity.coerceAtLeast(1), expires = expires, at = doc.stamp(), by = device))
    }

    /**
     * Moves [count] of an item to [space]; all of it when [count] is its quantity or more. What arrives
     * joins the same food with the same expiry day if the space has it.
     */
    fun moveItem(doc: PantryDocument, id: String, space: String, count: Int = Int.MAX_VALUE): PantryDocument {
        val item = doc.item(id) ?: return doc
        if (item.space == space) return doc
        val moving = count.coerceIn(1, item.quantity)
        val at = doc.stamp()
        val target = doc.sameFood(item.name, space, item.expires)
        var result = if (moving == item.quantity) {
            if (target == null) {
                return doc.withItem(item.copy(space = space, at = at, by = device))
            }
            doc.withItem(item.copy(deleted = true, at = at, by = device))
        } else {
            doc.withItem(item.copy(quantity = item.quantity - moving, at = at, by = device))
        }
        result = if (target != null) {
            result.withItem(target.copy(quantity = target.quantity + moving, at = at, by = device))
        } else {
            result.copy(items = result.items + item.copy(id = newId(), space = space, quantity = moving, at = at, by = device))
        }
        return result
    }

    /** Takes one of an item; the last one leaves the pantry. */
    fun useOne(doc: PantryDocument, id: String): PantryDocument {
        val item = doc.item(id) ?: return doc
        return if (item.quantity <= 1) removeItem(doc, id)
        else doc.withItem(item.copy(quantity = item.quantity - 1, at = doc.stamp(), by = device))
    }

    /** Takes an item out of the pantry, all of it: finished, thrown away or added by mistake. */
    fun removeItem(doc: PantryDocument, id: String): PantryDocument {
        val item = doc.item(id) ?: return doc
        return doc.withItem(item.copy(deleted = true, at = doc.stamp(), by = device))
    }

    /**
     * Puts the items whose ids are in [before] back as they were then; null there means the item did
     * not exist, so it is removed. Items changed since by someone else are put back too, as the user
     * asked for exactly this.
     */
    fun restore(doc: PantryDocument, before: Map<String, PantryItem?>): PantryDocument {
        val at = doc.stamp()
        val known = doc.items.associateBy { it.id }
        val restored = before.mapNotNull { (id, old) ->
            when {
                old != null -> old.copy(at = at, by = device)
                else -> known[id]?.copy(deleted = true, at = at, by = device)
            }
        }
        val ids = restored.mapTo(HashSet()) { it.id }
        return doc.copy(items = doc.items.filterNot { it.id in ids } + restored)
    }

    private fun PantryDocument.sameFood(name: String, space: String, expires: LocalDate?): PantryItem? =
        liveItems().firstOrNull { it.space == space && it.expires == expires && nameKey(it.name) == nameKey(name) }

    private fun PantryDocument.withItem(item: PantryItem): PantryDocument =
        copy(items = items.map { if (it.id == item.id) item else it })
}

/** The items that differ between two versions, as they were in [before]; null for ones [before] lacked. */
fun changedItems(before: PantryDocument, after: PantryDocument): Map<String, PantryItem?> {
    val old = before.items.associateBy { it.id }
    return after.items.filter { old[it.id] != it }.associate { it.id to old[it.id] }
}
