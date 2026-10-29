package eu.studiodeanna.openpantrymanager.ui

/**
 * Reminders the platform shows outside the app, once a day, of the food to use soon; each platform
 * that can provides them. Whether they are wanted is a setting; this says whether the system allows them.
 */
interface Reminders {
    /** Whether the system lets the app show reminders. */
    fun allowed(): Boolean

    /** Asks the user to let the app show reminders, where the system wants that. Returns whether it may. */
    suspend fun allow(): Boolean
}

/** Where the app was opened from outside it, such as a shortcut of its launcher icon, to go straight to. */
enum class Shortcut { Search, Voice, Receipt }
