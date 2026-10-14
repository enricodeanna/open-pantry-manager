package eu.studiodeanna.openpantrymanager.store

import eu.studiodeanna.openpantrymanager.sync.ShareLink
import kotlinx.serialization.Serializable

/**
 * This device's id, which stamps its edits, the shopping list it is linked to and the shared pantry
 * it is in. Never leaves the device, but for the shopping list's link, which a shared pantry shares.
 */
@Serializable
data class DeviceState(
    val device: String,
    val shoppingLink: ShareLink? = null,
    val shoppingSyncedAt: Long? = null,
    /** When the shopping list was last linked or unlinked, and on which device; 0 before pantries were shared. */
    val shoppingLinkAt: Long = 0,
    val shoppingLinkBy: String = "",
    /** The shared pantry's file; null while the pantry is this device's alone. */
    val pantryLink: ShareLink? = null,
    val pantrySyncedAt: Long? = null,
    /** Sublists of the shopping list folded away on this device. */
    val shoppingCollapsed: Set<String> = emptySet(),
    /** Spaces of the pantry folded away on this device. */
    val collapsedSpaces: Set<String> = emptySet(),
)
