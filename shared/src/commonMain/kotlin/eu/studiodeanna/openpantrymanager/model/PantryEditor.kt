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
        bought: LocalDate? = null,
    ): Pair<PantryDocument, String> {
        val at = doc.stamp()
        val same = doc.sameFood(name, space, expires)
        if (same != null) {
            val counted = same.copy(quantity = same.quantity + quantity, bought = earlier(same.bought, bought), at = at, by = device)
            return doc.withItem(counted) to same.id
        }
        val item = PantryItem(newId(), name.trim(), space, quantity.coerceAtLeast(1), expires, bought, added = now(), at = at, by = device)
        return doc.copy(items = doc.items + item) to item.id
    }

    /** Changes an item's name, quantity, expiry day and day it was bought; where it is changes with [moveItem]. */
    fun editItem(doc: PantryDocument, id: String, name: String, quantity: Int, expires: LocalDate?, bought: LocalDate?): PantryDocument {
        val item = doc.item(id) ?: return doc
        val edited = item.copy(name = name.trim(), quantity = quantity.coerceAtLeast(1), expires = expires, bought = bought, at = doc.stamp(), by = device)
        return doc.withItem(edited)
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
            result.withItem(target.copy(quantity = target.quantity + moving, bought = earlier(target.bought, item.bought), at = at, by = device))
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
     * Takes [change] back as an edit of its own: each space and item it touched returns to how it was
     * (one it created is deleted), unless it has been changed again since, by another device of a
     * shared pantry say, which is left as it is.
     */
    fun revert(doc: PantryDocument, change: PantryChange): PantryDocument {
        val at = doc.stamp()
        val spaces = change.spaces.associateBy { (_, new) -> new.id }
        val items = change.items.associateBy { (_, new) -> new.id }
        return doc.copy(
            spaces = doc.spaces.map { current ->
                val (old, new) = spaces[current.id] ?: return@map current
                if (!current.sameAs(new)) current else (old ?: new.copy(deleted = true)).copy(at = at, by = device)
            },
            items = doc.items.map { current ->
                val (old, new) = items[current.id] ?: return@map current
                if (!current.sameAs(new)) current else (old ?: new.copy(deleted = true)).copy(at = at, by = device)
            },
        )
    }

    /** Of two days an item was bought, the earlier: food counted together keeps its oldest. */
    private fun earlier(a: LocalDate?, b: LocalDate?): LocalDate? = listOfNotNull(a, b).minOrNull()

    private fun PantryDocument.sameFood(name: String, space: String, expires: LocalDate?): PantryItem? =
        liveItems().firstOrNull { it.space == space && it.expires == expires && nameKey(it.name) == nameKey(name) }

    private fun PantryDocument.withItem(item: PantryItem): PantryDocument =
        copy(items = items.map { if (it.id == item.id) item else it })
}
