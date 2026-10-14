package eu.studiodeanna.openpantrymanager.store

import eu.studiodeanna.openpantrymanager.sync.ShareLink
import kotlinx.serialization.Serializable

/**
 * This device's id, which stamps its edits, and the shopping list it is linked to. Never leaves the
 * device.
 */
@Serializable
data class DeviceState(
    val device: String,
    val shoppingLink: ShareLink? = null,
    val shoppingSyncedAt: Long? = null,
    /** Sublists of the shopping list folded away on this device. */
    val shoppingCollapsed: Set<String> = emptySet(),
    /** Spaces of the pantry folded away on this device. */
    val collapsedSpaces: Set<String> = emptySet(),
)
