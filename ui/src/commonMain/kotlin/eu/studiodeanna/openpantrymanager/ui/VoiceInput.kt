package eu.studiodeanna.openpantrymanager.ui

import eu.studiodeanna.openpantrymanager.voice.VoiceLanguage

/**
 * Hears what is said to the app, on the device, with an open-source speech recognizer; each platform
 * that can provides one. The speech model of a language is downloaded the first time it is needed.
 */
interface VoiceInput {
    /** Whether the speech model of [language] is on the device. */
    fun hasModel(language: VoiceLanguage): Boolean

    /** Downloads the speech model of [language], with [progress] from 0 to 1. Throws [VoiceFailed]. */
    suspend fun download(language: VoiceLanguage, progress: (Float) -> Unit)

    /**
     * Listens until a sentence is said, showing what is heard so far through [partial]. Returns the
     * sentence as heard freely (first, even if blank), and as heard when listening for [words] only (of
     * commands and food), which hears those better; or null when nothing was heard. Throws [VoiceFailed].
     */
    suspend fun listen(language: VoiceLanguage, words: List<String>, partial: (String) -> Unit): List<String>?

    /** Stops listening, and frees the memory the recognizer takes. */
    fun finish()
}

/** Voice input that could not work, with a [message] for the user. */
class VoiceFailed(message: String, cause: Throwable? = null) : Exception(message, cause)
