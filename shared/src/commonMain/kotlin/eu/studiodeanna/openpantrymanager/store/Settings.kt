package eu.studiodeanna.openpantrymanager.store

import kotlinx.serialization.Serializable

/** This device's preferences. Never leaves the device. */
@Serializable
data class Settings(
    val theme: ThemeChoice = ThemeChoice.System,
    val language: LanguageChoice = LanguageChoice.System,
    /** Whether finishing a food offers to put it on the shopping list. */
    val offerShopping: Boolean = true,
    /** Whether the phone says, once a day, which food is to be used soon. */
    val expiryReminders: Boolean = true,
)

@Serializable
enum class ThemeChoice { System, Light, Dark }

@Serializable
enum class LanguageChoice { System, English, Italian }
