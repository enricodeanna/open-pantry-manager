package eu.studiodeanna.openpantrymanager.voice

import eu.studiodeanna.openpantrymanager.memory.Memory
import eu.studiodeanna.openpantrymanager.memory.placed
import eu.studiodeanna.openpantrymanager.model.PantryDocument
import eu.studiodeanna.openpantrymanager.model.PantryEditor
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

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
        // Heard freely as something else entirely, and forced into words of food: neither is a command.
        assertEquals(VoiceCommand.NotUnderstood("che tempo fa oggi"), understandVoice(listOf("che tempo fa oggi", "sette"), Italian, italian.doc, italian.memory))
        // The words to listen for take in the pantry's own.
        val words = voiceVocabulary(Italian, italian.doc, italian.memory)
        assert("frizzante" in words && "cantina" in words && "uova" in words && "caffè" in words)
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
    fun everyLanguageOfTheAppHasVoiceCommands() {
        assertEquals(setOf("en", "it"), VoiceLanguages.all.map { it.code }.toSet())
    }
}
