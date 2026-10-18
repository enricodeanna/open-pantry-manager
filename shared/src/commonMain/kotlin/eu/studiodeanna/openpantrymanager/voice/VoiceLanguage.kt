package eu.studiodeanna.openpantrymanager.voice

/**
 * The speech recognizer's model for one language: a Vosk model, downloaded once from [url] (checked
 * against [sha256]) and unpacked into a folder called [name].
 */
data class SpeechModel(
    val name: String,
    val url: String,
    val sha256: String,
    /** The download's size, to tell the user before it starts. */
    val bytes: Long,
)

/**
 * The words voice commands are made of in one language, and the speech model that hears them. Every
 * language the app is translated into has one: adding a language means adding one of these, with a
 * small Vosk model of that language (https://alphacephei.com/vosk/models). Words are written as
 * [eu.studiodeanna.openpantrymanager.model.nameKey] makes them: lower case, without accents or
 * apostrophes ("l'acqua" is "l acqua").
 */
class VoiceLanguage(
    /** The language's ISO 639-1 code, as the app's translations are chosen by. */
    val code: String,
    val model: SpeechModel,
    /** Verbs saying some of a food was used: eaten, drunk, cooked. "Used up" comes under [finish]. */
    val use: Set<String>,
    /** Verbs, or phrases of a few words, saying a food is finished. */
    val finish: Set<String>,
    /** Verbs saying food was bought or put in the pantry. */
    val add: Set<String>,
    /** Verbs saying food was moved from one space to another. */
    val move: Set<String>,
    /**
     * Verbs saying food was taken: from a space of the pantry, it was used ("ho preso due uova dal
     * frigorifero"); else it was bought ("ho preso il pane"), which is shown to check before it is added.
     */
    val take: Set<String> = emptySet(),
    /** Numbers as said, up to what one buys at once. */
    val numbers: Map<String, Int>,
    /** Words saying all of it. */
    val all: Set<String>,
    /** Words that say nothing about which food: subjects, auxiliaries, articles, "of". */
    val fillers: Set<String>,
    /** What food comes in: boxes, bottles, packs. They are not part of its name. */
    val containers: Set<String>,
    /** Prepositions before the space food goes to ("in the fridge"). */
    val into: Set<String>,
    /** Prepositions before the space food comes from ("from the cellar"). */
    val from: Set<String>,
    /** A word without its plural or gender ending, so "mela" and "mele", "egg" and "eggs" are one word. */
    val stem: (String) -> String,
    /**
     * Words of common food, singular and plural, as written (with accents). The recognizer is also told to hear only the words of
     * commands and of food (these, and those of the pantry), which it hears better that way; and food
     * named with them is food, not a mishearing ("sei uova", not "se vuoi").
     */
    val foods: Set<String>,
    /** Sentences to show as examples, in the user's own words. */
    val examples: List<String>,
    /**
     * Words of the commands above as the recognizer writes them, where that differs from how they are
     * compared: with accents and apostrophes ("più", "c'è", "we're"). The recognizer is told to listen
     * for these too.
     */
    val spoken: Set<String> = emptySet(),
)

/** The languages voice commands are understood in. */
object VoiceLanguages {
    val all: List<VoiceLanguage> = listOf(English, Italian)

    /** The voice commands of the language with this ISO code; null if there are none yet. */
    fun forCode(code: String): VoiceLanguage? = all.firstOrNull { it.code == code }
}
