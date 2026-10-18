package eu.studiodeanna.openpantrymanager.voice

import eu.studiodeanna.openpantrymanager.memory.Memory
import eu.studiodeanna.openpantrymanager.memory.phraseMeans
import eu.studiodeanna.openpantrymanager.memory.placed
import eu.studiodeanna.openpantrymanager.model.PantryDocument
import eu.studiodeanna.openpantrymanager.model.PantryEditor
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Sentences as the speech recognizer wrote them down in tests with synthesized speech, slips and all
 * ("o consumato" for "ho consumato", "I eat" for "I ate").
 */
class VoiceCommandTest {
    private val today = LocalDate(2026, 10, 17)
    private val editor = PantryEditor("dev") { 1 }

    private class Pantry(val doc: PantryDocument, val memory: Memory, val spaces: Map<String, String>) {
        fun item(name: String, space: String) = doc.items.single { it.name == name && it.space == spaces.getValue(space) && !it.deleted }
    }

    private fun pantry(spaceNames: List<String>, vararg food: Triple<String, String, Pair<Int, LocalDate?>>, known: List<Pair<String, String>> = emptyList()): Pantry {
        var doc = PantryDocument()
        val spaces = spaceNames.associateWith { name -> editor.addSpace(doc, name).also { doc = it.first }.second }
        for ((name, space, details) in food) doc = editor.addItem(doc, name, spaces.getValue(space), details.first, details.second).first
        var memory = Memory()
        for ((name, space) in known) memory = memory.placed(name, spaces.getValue(space), 0, today)
        return Pantry(doc, memory, spaces)
    }

    private val italian = pantry(
        listOf("Frigorifero", "Dispensa", "Cantina"),
        Triple("Mele", "Cantina", 4 to null),
        Triple("Latte", "Frigorifero", 2 to LocalDate(2026, 10, 20)),
        Triple("Latte", "Cantina", 1 to LocalDate(2026, 10, 25)),
        Triple("Passata di pomodoro", "Dispensa", 3 to null),
        Triple("Pasta al pomodoro", "Frigorifero", 1 to null),
        Triple("Acqua frizzante", "Cantina", 6 to null),
        known = listOf("Uova" to "Frigorifero"),
    )

    private fun it(heard: String) = understandVoice(heard, Italian, italian.doc, italian.memory)

    @Test
    fun italianSentencesAboutUsingAndFinishing() {
        assertEquals(VoiceCommand.Use(italian.item("Mele", "Cantina"), 1), it("ho mangiato una mela"))
        assertEquals(VoiceCommand.Use(italian.item("Passata di pomodoro", "Dispensa"), 2), it("o consumato due confezioni di passata di pomodoro"))
        assertEquals(VoiceCommand.Use(italian.item("Acqua frizzante", "Cantina"), 1), it("ho bevuto una bottiglia di acqua frizzante"))
        // Of two, the milk expiring first.
        assertEquals(VoiceCommand.Finish(italian.item("Latte", "Frigorifero")), it("ho finito il latte"))
        // "al pomodoro" is part of the food, not a space.
        assertEquals(VoiceCommand.Finish(italian.item("Pasta al pomodoro", "Frigorifero")), it("ho mangiato tutta la pasta al pomodoro"))
        assertEquals(VoiceCommand.NotUnderstood("che tempo fa"), it("che tempo fa"))
    }

    @Test
    fun italianSentencesAboutBuyingAndMoving() {
        assertEquals(VoiceCommand.Add("Uova", 6, italian.spaces["Frigorifero"]), it("ho comprato sei uova"))
        // New food is called as said, and goes where it was said to go; or nowhere yet.
        assertEquals(VoiceCommand.Add("Yogurt", 1, italian.spaces["Frigorifero"]), it("ho messo delle yogurt nel frigorifero"))
        assertEquals(VoiceCommand.Add("Kiwi", 3, null), it("ho comprato tre kiwi"))
        // The milk not in the fridge already moves, all of it.
        assertEquals(VoiceCommand.Move(italian.item("Latte", "Cantina"), 1, italian.spaces["Frigorifero"]), it("ho spostato il latte in frigorifero"))
        assertEquals(
            VoiceCommand.Move(italian.item("Acqua frizzante", "Cantina"), 2, italian.spaces["Dispensa"]),
            it("ho spostato due bottiglie di acqua dalla cantina alla dispensa"),
        )
    }

    /** As the emulator heard a recording of "ho comprato sei uova nel frigorifero": freely, then as words of food. */
    @Test
    fun ofTwoHearingsTheOneNamingFoodIsTaken() {
        val freely = "ho comprato se vuoi nel frigorifero"
        assertEquals(VoiceCommand.Add("Se vuoi", 1, italian.spaces["Frigorifero"], sure = false), it(freely))
        assertEquals(
            VoiceCommand.Add("Uova", 6, italian.spaces["Frigorifero"]),
            understandVoice(listOf(freely, "ho comprato sei uova nel frigorifero"), Italian, italian.doc, italian.memory),
        )
        // Freely heard without a verb, it is checked with the user first: the verb may be a word not known, forced.
        assertEquals(
            VoiceCommand.Check(VoiceCommand.Use(italian.item("Acqua frizzante", "Cantina"), 2), "togli due bottiglie di acqua frizzante"),
            understandVoice(listOf("tuoi due bottiglie di acqua frizzante", "togli due bottiglie di acqua frizzante"), Italian, italian.doc, italian.memory),
        )
        assertEquals(
            VoiceCommand.Check(VoiceCommand.Use(italian.item("Latte", "Frigorifero"), 1), "ho visto che il latte nel frigorifero e bevuto"),
            understandVoice(
                listOf("ho visto che il latte nel frigorifero è scaduto", "ho visto che il latte nel frigorifero e bevuto"),
                Italian,
                italian.doc,
                italian.memory,
            ),
        )
        // Heard freely as something else entirely, and forced into words of food: neither is a command.
        assertEquals(VoiceCommand.NotUnderstood("che tempo fa oggi"), understandVoice(listOf("che tempo fa oggi", "sette"), Italian, italian.doc, italian.memory))
        // Words not known, forced into known ones far from what was said, are not done: "bevuto" was not said.
        assertEquals(
            VoiceCommand.NotUnderstood("il latte e scaduto"),
            understandVoice(listOf("il latte e scaduto", "latte pesca bevuto"), Italian, italian.doc, italian.memory),
        )
        // Nor is what only the words of food made of nothing heard freely.
        assertEquals(VoiceCommand.NotUnderstood("ho bevuto il latte"), understandVoice(listOf("", "ho bevuto il latte"), Italian, italian.doc, italian.memory))
        // The words to listen for take in the pantry's own.
        val words = voiceVocabulary(Italian, italian.doc, italian.memory)
        assert("frizzante" in words && "cantina" in words && "uova" in words && "caffè" in words)
    }

    @Test
    fun moreItalianWaysOfSayingIt() {
        assertEquals(VoiceCommand.Add("Mele", 3, null), it("mi hanno regalato tre mele"))
        // As the emulator heard a recording of it.
        assertEquals(
            VoiceCommand.Add("Mele", 3, null),
            understandVoice(listOf("mi hanno regalato te mele", "mi hanno regalato tre mele"), Italian, italian.doc, italian.memory),
        )
        assertEquals(VoiceCommand.Use(italian.item("Acqua frizzante", "Cantina"), 2), it("togli due bottiglie di acqua frizzante"))
        assertEquals(VoiceCommand.Use(italian.item("Latte", "Frigorifero"), 1), it("ho buttato via il latte"))
        assertEquals(VoiceCommand.Finish(italian.item("Latte", "Frigorifero")), it("non c'è più latte"))
        assertEquals(VoiceCommand.Finish(italian.item("Passata di pomodoro", "Dispensa")), it("manca la passata"))
        assertEquals(VoiceCommand.Add("Uova", 24, italian.spaces["Frigorifero"]), it("ho comprato ventiquattro uova"))
    }

    /** Who gives says which way food went: given away is used, given to the speaker is bought. */
    @Test
    fun givenAwayOrReceived() {
        assertEquals(VoiceCommand.Use(italian.item("Mele", "Cantina"), 2), it("ho dato due mele al vicino"))
        assertEquals(VoiceCommand.Use(italian.item("Passata di pomodoro", "Dispensa"), 1), it("abbiamo regalato la passata ai vicini"))
        assertEquals(VoiceCommand.Use(italian.item("Mele", "Cantina"), 3), it("gli ho dato tre mele"))
        assertEquals(VoiceCommand.Add("Mele", 2, null), it("mi hanno dato due mele"))
        assertEquals(VoiceCommand.Add("Mele", 1, null), it("le mele me le hanno regalate"))
        assertEquals(VoiceCommand.Add("Acqua frizzante", 1, null), it("mi è stata regalata una bottiglia di acqua frizzante"))
        assertEquals(VoiceCommand.Add("Mele", 3, null), it("ci ha portato tre mele"))
        // Without anyone giving, "portato" still moves.
        assertEquals(
            VoiceCommand.Move(italian.item("Latte", "Cantina"), 1, italian.spaces["Frigorifero"]),
            it("ho portato il latte in frigorifero"),
        )

        assertEquals(VoiceCommand.Use(english.item("Apples", "Pantry"), 2), en("I gave two apples to the neighbours"))
        assertEquals(VoiceCommand.Add("Apples", 2, null), en("they gave us two apples"))
        assertEquals(VoiceCommand.Add("Eggs", 6, english.spaces["Fridge"]), en("she gave me six eggs"))
    }

    /** "Preso": taken out of a space of the pantry, used; else bought, shown to check first. */
    @Test
    fun takenFromThePantryOrBought() {
        assertEquals(VoiceCommand.Use(italian.item("Acqua frizzante", "Cantina"), 2), it("ho preso due bottiglie di acqua frizzante dalla cantina"))
        assertEquals(VoiceCommand.Use(italian.item("Latte", "Cantina"), 1), it("ho preso il latte dalla cantina"))
        assertEquals(VoiceCommand.Add("Latte", 1, null, sure = false), it("ho preso il latte al supermercato"))
        assertEquals(VoiceCommand.Add("Uova", 6, italian.spaces["Frigorifero"], sure = false), it("ho preso sei uova"))
        assertEquals(VoiceCommand.Add("Uova", 6, italian.spaces["Frigorifero"]), it("ho comprato sei uova al supermercato"))
    }

    /** A phrase the app does not know is taught once, then understood in its other forms too. */
    @Test
    fun aPhraseTaughtIsUnderstoodFromThenOn() {
        assertEquals(VoiceCommand.NotUnderstood("il latte è scaduto"), it("il latte è scaduto"))
        assertEquals("scaduto", phraseToLearn("il latte è scaduto", Italian, italian.doc, italian.memory))
        // Small words inside a phrase are part of it; numbers and food end it.
        assertEquals("afferrato al volo", phraseToLearn("ho afferrato al volo tre mele", Italian, italian.doc, italian.memory))
        // A sentence with a verb known lacks food, not words: there is nothing to teach.
        assertNull(phraseToLearn("ho mangiato una pitaya", Italian, italian.doc, italian.memory))
        assertEquals("che tempo fa", phraseToLearn("che tempo fa oggi", Italian, italian.doc, italian.memory))

        val taught = italian.memory.phraseMeans("it", "scaduto", VoiceAction.Finish)
        assertEquals(VoiceCommand.Finish(italian.item("Latte", "Frigorifero")), understandVoice("il latte è scaduto", Italian, italian.doc, taught))
        assertEquals(VoiceCommand.Finish(italian.item("Mele", "Cantina")), understandVoice("le mele sono scadute", Italian, italian.doc, taught))
        assertNull(phraseToLearn("il latte è scaduto", Italian, italian.doc, taught))
        // Heard better from now on, and only in its language.
        assert("scaduto" in voiceVocabulary(Italian, italian.doc, taught))
        assertEquals(VoiceCommand.NotUnderstood("the milk scaduto"), understandVoice("the milk scaduto", English, english.doc, taught))
    }

    private val english = pantry(
        listOf("Fridge", "Pantry", "Cellar"),
        Triple("Apples", "Pantry", 3 to null),
        Triple("Milk", "Fridge", 2 to null),
        Triple("Tomato sauce", "Pantry", 2 to null),
        known = listOf("Eggs" to "Fridge"),
    )

    private fun en(heard: String) = understandVoice(heard, English, english.doc, english.memory)

    @Test
    fun englishSentences() {
        assertEquals(VoiceCommand.Use(english.item("Apples", "Pantry"), 1), en("i eat an apple"))
        assertEquals(VoiceCommand.Use(english.item("Tomato sauce", "Pantry"), 2), en("i consume two boxes of tomato sauce"))
        assertEquals(VoiceCommand.Finish(english.item("Milk", "Fridge")), en("i finish the milk"))
        assertEquals(VoiceCommand.Finish(english.item("Milk", "Fridge")), en("I ran out of milk"))
        assertEquals(VoiceCommand.Add("Eggs", 12, english.spaces["Fridge"]), en("i bought a dozen eggs"))
        assertEquals(VoiceCommand.Move(english.item("Milk", "Fridge"), 2, english.spaces["Cellar"]), en("i moved the milk to the cellar"))
    }

    @Test
    fun moreEnglishWaysOfSayingIt() {
        assertEquals(VoiceCommand.Add("Apples", 1, null), en("they gave me some apples"))
        assertEquals(VoiceCommand.Finish(english.item("Milk", "Fridge")), en("we're out of milk"))
        // The first verb said: "out of" here says where from.
        assertEquals(VoiceCommand.Use(english.item("Apples", "Pantry"), 2), en("I took two apples out of the pantry"))
        assertEquals(VoiceCommand.Use(english.item("Tomato sauce", "Pantry"), 1), en("I threw away the tomato sauce"))
        assertEquals(VoiceCommand.Add("Eggs", 24, english.spaces["Fridge"]), en("I bought twenty four eggs"))
        assertEquals(VoiceCommand.Finish(english.item("Milk", "Fridge")), en("the milk has all gone"))
        // As the recognizer heard it, freely and as words of commands: the number of the second.
        assertEquals(
            VoiceCommand.Use(english.item("Apples", "Pantry"), 2),
            understandVoice(listOf("i took to apples out of the pantry", "i took two apples out of the pantry"), English, english.doc, english.memory),
        )
    }

    @Test
    fun anEnglishPhraseTaught() {
        assertEquals("nicked", phraseToLearn("I nicked two apples", English, english.doc, english.memory))
        val taught = english.memory.phraseMeans("en", "Nicked", VoiceAction.Use)
        assertEquals(VoiceCommand.Use(english.item("Apples", "Pantry"), 2), understandVoice("I nicked two apples", English, english.doc, taught))
    }

    @Test
    fun everyLanguageOfTheAppHasVoiceCommands() {
        assertEquals(setOf("en", "it"), VoiceLanguages.all.map { it.code }.toSet())
    }
}
