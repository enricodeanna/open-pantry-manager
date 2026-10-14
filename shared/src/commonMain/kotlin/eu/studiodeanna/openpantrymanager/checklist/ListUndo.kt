package eu.studiodeanna.openpantrymanager.checklist

import eu.studiodeanna.openpantrymanager.model.Stamped
import eu.studiodeanna.openpantrymanager.model.Step

/**
 * What one edit of the shopping list changed: each section and item it touched as it was before
 * (null for one it created) and after. [ListEditor.revert] takes it back as a new edit, so an undo
 * syncs like any other. As Open Check Lists keeps it.
 */
data class ListChange(
    val sections: List<Pair<Section?, Section>> = emptyList(),
    val items: List<Pair<Item?, Item>> = emptyList(),
)

/** What [before] became in [after], or null if nothing reads differently; new stamps alone do not count. */
fun listChangeBetween(before: ListDocument, after: ListDocument): ListChange? {
    val change = ListChange(sections = changed(before.sections, after.sections), items = changed(before.items, after.items))
    return change.takeUnless { it.sections.isEmpty() && it.items.isEmpty() }
}

private fun <T : Stamped> changed(before: List<T>, after: List<T>): List<Pair<T?, T>> {
    val old = before.associateBy { it.id }
    return after.filterNot { it.sameAs(old[it.id]) }.map { old[it.id] to it }
}

/** Whether [other] is this section or item saying the same, whoever stamped it and when. */
internal fun Stamped.sameAs(other: Stamped?): Boolean = when (this) {
    is Section -> other is Section && copy(at = other.at, by = other.by) == other
    is Item -> other is Item && copy(at = other.at, by = other.by) == other
    else -> this == other
}

/** What the user did in one edit of the list, to name it ("Undone: checked “Milk”"). */
sealed interface ListAction {
    data class AddedItem(val text: String) : ListAction
    data class CheckedItem(val text: String) : ListAction
    data class UncheckedItem(val text: String) : ListAction
    /** Anything else. */
    data object Other : ListAction
}

/** An edit of the shopping list made on this device, to undo and redo. */
typealias ListStep = Step<ListChange, ListAction>

/** Names the edit that made this change. */
fun ListChange.action(): ListAction {
    val (old, new) = items.singleOrNull() ?: return ListAction.Other
    return when {
        old == null -> ListAction.AddedItem(new.text)
        old.done != new.done -> if (new.done) ListAction.CheckedItem(new.text) else ListAction.UncheckedItem(new.text)
        else -> ListAction.Other
    }
}
