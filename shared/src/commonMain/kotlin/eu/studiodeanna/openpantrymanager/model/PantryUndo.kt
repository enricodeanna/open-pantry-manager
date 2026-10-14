package eu.studiodeanna.openpantrymanager.model

import eu.studiodeanna.openpantrymanager.memory.Learned
import eu.studiodeanna.openpantrymanager.memory.Memory
import eu.studiodeanna.openpantrymanager.memory.learned

/**
 * What one edit of the pantry changed: each space and item it touched as it was before (null for one
 * it created) and after, and the things the app learned from it, likewise (null: not known). Taking it
 * back is an edit of its own ([PantryEditor.revert]), which syncs like any other.
 */
class PantryChange internal constructor(
    val spaces: List<Pair<Space?, Space>>,
    val items: List<Pair<PantryItem?, PantryItem>>,
    internal val learned: Map<String, Pair<Learned?, Learned?>>,
)

/** What the user did in one edit, to name it ("Undone: used one “Milk”"). */
sealed interface PantryAction {
    data class AddedFood(val name: String) : PantryAction
    data class UsedOne(val name: String) : PantryAction
    data class Finished(val name: String) : PantryAction
    data class DeletedFood(val name: String) : PantryAction
    data class EditedFood(val name: String) : PantryAction
    data class MovedFood(val name: String) : PantryAction
    data class PutAway(val count: Int) : PantryAction
    data class AddedSpace(val name: String) : PantryAction
    data class RenamedSpace(val name: String) : PantryAction
    data class MovedSpace(val name: String) : PantryAction
    data class DeletedSpace(val name: String) : PantryAction
}

/** An edit of the pantry made on this device, to undo and redo. */
typealias PantryStep = Step<PantryChange, PantryAction>

/**
 * What [before] became in [after], with the app's knowledge going from [known] to [learned]; null if
 * nothing reads differently. New stamps alone do not count.
 */
fun pantryChangeBetween(before: PantryDocument, after: PantryDocument, known: Memory, learned: Memory): PantryChange? {
    val was = known.learned()
    val now = learned.learned()
    val change = PantryChange(
        spaces = changed(before.spaces, after.spaces),
        items = changed(before.items, after.items),
        learned = (was.keys + now.keys).filter { was[it] != now[it] }.associateWith { was[it] to now[it] },
    )
    return change.takeUnless { it.spaces.isEmpty() && it.items.isEmpty() && it.learned.isEmpty() }
}

private fun <T : Stamped> changed(before: List<T>, after: List<T>): List<Pair<T?, T>> {
    val old = before.associateBy { it.id }
    return after.filterNot { it.sameAs(old[it.id]) }.map { old[it.id] to it }
}

/** Whether [other] is this space or item saying the same, whoever stamped it and when. */
internal fun Stamped.sameAs(other: Stamped?): Boolean = when (this) {
    is Space -> other is Space && copy(at = other.at, by = other.by) == other
    is PantryItem -> other is PantryItem && copy(at = other.at, by = other.by) == other
    else -> this == other
}
