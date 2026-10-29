package eu.studiodeanna.openpantrymanager.model

import eu.studiodeanna.openpantrymanager.costs.CostsDocument
import eu.studiodeanna.openpantrymanager.costs.Measure
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
     * Puts [quantity] of [name] in [space], in closed boxes if [boxed], of [size] each if known. If the
     * space already holds that food with the same expiry day and size, not opened, it is counted there
     * instead of listed twice. Returns the document and the item's id.
     */
    fun addItem(
        doc: PantryDocument,
        name: String,
        space: String,
        quantity: Int = 1,
        expires: LocalDate? = null,
        bought: LocalDate? = null,
        boxed: Boolean = false,
        size: Measure? = null,
    ): Pair<PantryDocument, String> {
        val at = doc.stamp()
        val same = doc.sameFood(name, space, expires, opened = null, size)
        if (same != null) {
            val counted = same.copy(
                quantity = same.quantity + quantity,
                bought = earlier(same.bought, bought),
                boxed = same.boxed || boxed,
                at = at,
                by = device,
            )
            return doc.withItem(counted) to same.id
        }
        val item = PantryItem(newId(), name.trim(), space, quantity.coerceAtLeast(1), expires, bought, boxed, size = size, added = now(), at = at, by = device)
        return doc.copy(items = doc.items + item) to item.id
    }

    /**
     * Changes an item's name, quantity, expiry day and day it was bought, whether it comes in boxes, the
     * day they were opened (null: closed), their size and what is left of the opened ones; where it is
     * changes with [moveItem]. Only boxes open, and only opened boxes of a known size have some left.
     */
    fun editItem(
        doc: PantryDocument,
        id: String,
        name: String,
        quantity: Int,
        expires: LocalDate?,
        bought: LocalDate?,
        boxed: Boolean = doc.item(id)?.boxed ?: false,
        opened: LocalDate? = doc.item(id)?.opened,
        size: Measure? = doc.item(id)?.size,
        left: Double? = doc.item(id)?.left,
    ): PantryDocument {
        val item = doc.item(id) ?: return doc
        val count = quantity.coerceAtLeast(1)
        val isOpened = opened != null && boxed
        val edited = item.copy(
            name = name.trim(),
            quantity = count,
            expires = expires,
            bought = bought,
            boxed = boxed,
            opened = opened.takeIf { boxed },
            size = size,
            // Opened just now, it is full; else what is left, no more than the boxes hold.
            left = if (!isOpened || size == null) null else (left ?: (size.value * count)).coerceIn(0.0, size.value * count),
            at = doc.stamp(),
            by = device,
        )
        return doc.withItem(edited)
    }

    /**
     * Takes [amount] (in the unit of its size) out of an opened box of known size: what is left goes
     * down, and so does the count of boxes holding it; when nothing is left, it leaves the pantry.
     */
    fun useAmount(doc: PantryDocument, id: String, amount: Double): PantryDocument {
        val item = doc.item(id) ?: return doc
        val size = item.size ?: return doc
        val left = (item.left ?: (size.value * item.quantity)) - amount.coerceAtLeast(0.0)
        if (left <= EMPTY) return removeItem(doc, id)
        val boxes = kotlin.math.ceil(left / size.value - EMPTY).toInt().coerceIn(1, item.quantity)
        return doc.withItem(item.copy(left = left, quantity = boxes, at = doc.stamp(), by = device))
    }

    /**
     * Opens [count] closed boxes of an item on [day]: they become an item of their own, the same food
     * opened, and the rest stay closed; all of them when [count] is its quantity or more. Boxes of it
     * opened the same day, in the same space, are counted together. Food not in boxes so far comes in
     * boxes from now on. Returns the document and the opened item's id.
     */
    fun open(doc: PantryDocument, id: String, day: LocalDate, count: Int = 1): Pair<PantryDocument, String> {
        val item = doc.item(id)?.takeIf { it.opened == null } ?: return doc to id
        val at = doc.stamp()
        val opening = count.coerceIn(1, item.quantity)
        // Boxes of a known size are followed one by one, each with what is left of it.
        val twin = doc.sameFood(item.name, item.space, item.expires, day, item.size).takeIf { item.size == null }
        val full = item.size?.let { it.value * opening }
        if (twin != null) {
            val rest = if (opening == item.quantity) item.copy(deleted = true) else item.copy(quantity = item.quantity - opening, boxed = true)
            val joined = twin.copy(quantity = twin.quantity + opening, bought = earlier(twin.bought, item.bought), at = at, by = device)
            return doc.withItem(rest.copy(at = at, by = device)).withItem(joined) to twin.id
        }
        if (opening == item.quantity) {
            return doc.withItem(item.copy(boxed = true, opened = day, left = full, at = at, by = device)) to id
        }
        val opened = item.copy(id = newId(), quantity = opening, boxed = true, opened = day, left = full, at = at, by = device)
        val closed = item.copy(quantity = item.quantity - opening, boxed = true, at = at, by = device)
        val withClosed = doc.withItem(closed)
        return withClosed.copy(items = withClosed.items + opened) to opened.id
    }

    /**
     * Moves [count] of an item to [space]; all of it when [count] is its quantity or more. What arrives
     * joins the same food with the same expiry day, opened the same day or closed, if the space has it.
     */
    fun moveItem(doc: PantryDocument, id: String, space: String, count: Int = Int.MAX_VALUE): PantryDocument {
        val item = doc.item(id) ?: return doc
        if (item.space == space) return doc
        // Opened boxes with some left go together.
        val moving = if (item.left != null) item.quantity else count.coerceIn(1, item.quantity)
        val at = doc.stamp()
        val target = doc.sameFood(item.name, space, item.expires, item.opened, item.size).takeIf { item.left == null || it?.left != null }
        var result = if (moving == item.quantity) {
            if (target == null) {
                return doc.withItem(item.copy(space = space, at = at, by = device))
            }
            doc.withItem(item.copy(deleted = true, at = at, by = device))
        } else {
            doc.withItem(item.copy(quantity = item.quantity - moving, at = at, by = device))
        }
        result = if (target != null) {
            val joined = target.copy(
                quantity = target.quantity + moving,
                bought = earlier(target.bought, item.bought),
                boxed = target.boxed || item.boxed,
                left = if (target.left != null && item.left != null) target.left + item.left else target.left,
                at = at,
                by = device,
            )
            result.withItem(joined)
        } else {
            result.copy(items = result.items + item.copy(id = newId(), space = space, quantity = moving, at = at, by = device))
        }
        return result
    }

    /** Takes one of an item; the last one leaves the pantry. */
    fun useOne(doc: PantryDocument, id: String): PantryDocument = useSome(doc, id, 1)

    /** Takes [count] of an item; when that is all of it, it leaves the pantry. */
    fun useSome(doc: PantryDocument, id: String, count: Int): PantryDocument {
        val item = doc.item(id) ?: return doc
        return if (item.quantity <= count) removeItem(doc, id)
        else doc.withItem(item.copy(quantity = item.quantity - count.coerceAtLeast(1), at = doc.stamp(), by = device))
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

    /**
     * Takes back what [change] did to the costs, the way [revert] does to the pantry: each purchase it
     * touched returns to how it was (one it added is deleted), unless changed again since.
     */
    fun revertCosts(costs: CostsDocument, change: PantryChange): CostsDocument {
        if (change.purchases.isEmpty()) return costs
        val at = maxOf(now(), (costs.purchases.maxOfOrNull { it.at } ?: 0) + 1)
        val purchases = change.purchases.associateBy { (_, new) -> new.id }
        return costs.copy(
            purchases = costs.purchases.map { current ->
                val (old, new) = purchases[current.id] ?: return@map current
                if (!current.sameAs(new)) current else (old ?: new.copy(deleted = true)).copy(at = at, by = device)
            },
        )
    }

    /** Of two days an item was bought, the earlier: food counted together keeps its oldest. */
    private fun earlier(a: LocalDate?, b: LocalDate?): LocalDate? = listOfNotNull(a, b).minOrNull()

    private fun PantryDocument.sameFood(name: String, space: String, expires: LocalDate?, opened: LocalDate?, size: Measure?): PantryItem? =
        liveItems().firstOrNull {
            it.space == space && it.expires == expires && it.opened == opened && it.size == size && nameKey(it.name) == nameKey(name)
        }

    private companion object {
        /** Less than this is nothing left: what rounding leaves of grams and millilitres. */
        const val EMPTY = 0.001
    }

    private fun PantryDocument.withItem(item: PantryItem): PantryDocument =
        copy(items = items.map { if (it.id == item.id) item else it })
}
