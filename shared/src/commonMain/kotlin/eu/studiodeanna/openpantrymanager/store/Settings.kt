package eu.studiodeanna.openpantrymanager.store

import eu.studiodeanna.openpantrymanager.model.ItemOrder
import kotlinx.serialization.Serializable

/** This device's preferences. Never leaves the device. */
@Serializable
data class Settings(
    val theme: ThemeChoice = ThemeChoice.System,
    val language: LanguageChoice = LanguageChoice.System,
    val order: ItemOrder = ItemOrder.Name,
    /** Whether finishing a food offers to put it on the shopping list. */
    val offerShopping: Boolean = true,
)

@Serializable
enum class ThemeChoice { System, Light, Dark }

@Serializable
enum class LanguageChoice { System, English, Italian }
