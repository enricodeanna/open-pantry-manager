package eu.studiodeanna.openpantrymanager.model

/** An edit made on this device that can be taken back: what it changed, and what the user did. */
data class Step<C, A>(val change: C, val action: A)

/**
 * This device's latest edits, newest last, to undo and redo, as Open Check Lists keeps them for each
 * list. Kept only while the app runs.
 */
data class History<C, A>(val undo: List<Step<C, A>> = emptyList(), val redo: List<Step<C, A>> = emptyList()) {
    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    /** After a new edit: it can be undone, and what was undone before it can no longer be redone. */
    fun record(change: C, action: A): History<C, A> = History((undo + Step(change, action)).takeLast(LIMIT), emptyList())

    /** The step an undo ([redo] false) or a redo would take back next. */
    fun next(redo: Boolean): Step<C, A>? = (if (redo) this.redo else undo).lastOrNull()

    /**
     * After [next] was taken back by the edit [back]: it moves to the other list, so it can be taken
     * back in turn. With [back] null (everything in it was changed again since) it is just dropped.
     */
    fun took(redo: Boolean, back: C?): History<C, A> {
        val moved = back?.let { listOf(Step<C, A>(it, next(redo)!!.action)) }.orEmpty()
        return if (redo) History((undo + moved).takeLast(LIMIT), this.redo.dropLast(1))
        else History(undo.dropLast(1), this.redo + moved)
    }

    companion object {
        const val LIMIT = 10
    }
}

/** Whether there is anything to undo or redo, for the buttons that do it. */
data class UndoState(val canUndo: Boolean = false, val canRedo: Boolean = false)

val History<*, *>.state: UndoState get() = UndoState(canUndo, canRedo)
