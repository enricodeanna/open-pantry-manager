package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import eu.studiodeanna.openpantrymanager.voice.Italian
import eu.studiodeanna.openpantrymanager.voice.VoiceAction
import eu.studiodeanna.openpantrymanager.voice.VoiceLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class VoiceDialogTest {
    /** Hears [sentences] one by one, as the microphone would, with its model downloaded on request. */
    private class FakeVoice(private val sentences: ArrayDeque<String>) : VoiceInput {
        var model = false
        var finished = false
        override fun hasModel(language: VoiceLanguage) = model
        override suspend fun download(language: VoiceLanguage, progress: (Float) -> Unit) {
            progress(1f)
            model = true
        }
        override suspend fun listen(language: VoiceLanguage, words: List<String>, partial: (String) -> Unit): List<String>? =
            listOf(sentences.removeFirst())
        override fun finish() {
            finished = true
        }
    }

    @Test
    fun theModelIsDownloadedOnceAgreedThenWhatIsHeardIsDoneOrSaidNotUnderstood() = runComposeUiTest {
        val voice = FakeVoice(ArrayDeque(listOf("che tempo fa", "ho mangiato una mela")))
        val heard = mutableListOf<List<String>>()
        var open by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalStrings provides ItalianStrings) {
                if (open) {
                    VoiceDialog(
                        Italian,
                        voice,
                        words = { emptyList() },
                        onHeard = { sentence ->
                            heard += sentence
                            if (sentence.first() == "che tempo fa") Obeyed.NotUnderstood else Obeyed.Done.also { open = false }
                        },
                        teach = { null },
                        onLearn = { _, _ -> },
                        onDismiss = { open = false },
                    )
                }
            }
        }
        // Nothing is downloaded before the user agrees.
        onNodeWithText("Per capire cosa dici", substring = true).assertExists()
        onNodeWithText("Scarica").performClick()
        onNodeWithText("Non ho capito “che tempo fa”.").assertExists()
        onNodeWithText("Riprova").performClick()
        waitForIdle()
        assertEquals(listOf(listOf("che tempo fa"), listOf("ho mangiato una mela")), heard)
        // Closed, it lets the recognizer go.
        assertTrue(voice.finished)
    }

    /** "Mi hanno dato tre mele" before the app knew "dato": taught once, the same sentence is done. */
    @Test
    fun wordsNotKnownAreTaughtThenTheSentenceIsDone() = runComposeUiTest {
        val voice = FakeVoice(ArrayDeque(listOf("mi ha passato tre mele"))).apply { model = true }
        val taught = mutableMapOf<String, VoiceAction>()
        val done = mutableListOf<List<String>>()
        var open by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalStrings provides ItalianStrings) {
                if (open) {
                    VoiceDialog(
                        Italian,
                        voice,
                        words = { emptyList() },
                        onHeard = { sentence ->
                            if ("passato" !in taught) {
                                Obeyed.NotUnderstood
                            } else {
                                done += sentence
                                open = false
                                Obeyed.Done
                            }
                        },
                        teach = { "passato" },
                        onLearn = { phrase, action -> taught[phrase] = action },
                        onDismiss = { open = false },
                    )
                }
            }
        }
        onNodeWithText("Non ho capito “mi ha passato tre mele”.").assertExists()
        onNodeWithText("Insegnamelo").performClick()
        onNodeWithText("Insegnami una frase").assertExists()
        // Nothing is learned before saying what the words mean.
        onNodeWithText("Impara").assertIsNotEnabled()
        onNodeWithText("L'ho comprato, o messo in dispensa").performClick()
        onNodeWithText("Impara").performClick()
        waitForIdle()
        assertEquals(mapOf("passato" to VoiceAction.Add), taught)
        assertEquals(listOf(listOf("mi ha passato tre mele")), done)
    }

    /** Heard freely as "tuoi due uova", "togli" only listening for words of commands: it is asked first. */
    @Test
    fun whatOnlyWordsOfCommandsMadeSenseOfIsCheckedFirst() = runComposeUiTest {
        val voice = FakeVoice(ArrayDeque(listOf("tuoi due uova", "il latte scaduto"))).apply { model = true }
        val done = mutableListOf<List<String>>()
        var open by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalStrings provides ItalianStrings) {
                if (open) {
                    VoiceDialog(
                        Italian,
                        voice,
                        words = { emptyList() },
                        onHeard = { sentence ->
                            when (sentence.single()) {
                                "tuoi due uova" -> Obeyed.IfSaid("togli due uova")
                                "il latte scaduto" -> Obeyed.IfSaid("il latte bevuto")
                                else -> Obeyed.Done.also { done += sentence; open = false }
                            }
                        },
                        teach = { "scaduto" },
                        onLearn = { _, _ -> },
                        onDismiss = { open = false },
                    )
                }
            }
        }
        // Not what was said: not understood, and the words can be taught.
        onNodeWithText("Hai detto “togli due uova”?").assertExists()
        onNodeWithText("No").performClick()
        onNodeWithText("Non ho capito “tuoi due uova”.").assertExists()
        onNodeWithText("Insegnamelo").assertExists()
        onNodeWithText("Riprova").performClick()
        onNodeWithText("Hai detto “il latte bevuto”?").assertExists()
        onNodeWithText("Sì").performClick()
        waitForIdle()
        assertEquals(listOf(listOf("il latte bevuto")), done)
    }

    @Test
    fun taughtPhrasesAreListedForgottenAndAdded() = runComposeUiTest {
        var phrases by mutableStateOf(mapOf("è scaduto" to VoiceAction.Finish))
        setContent {
            CompositionLocalProvider(LocalStrings provides ItalianStrings) {
                TaughtPhrasesDialog(
                    phrases,
                    onLearn = { phrase, action -> phrases = phrases + (phrase to action) },
                    onForget = { phrases = phrases - it },
                    onDismiss = {},
                )
            }
        }
        onNodeWithText("“è scaduto”").assertExists()
        onNodeWithText("È finito, tutto").assertExists()
        onNodeWithContentDescription("Dimentica “è scaduto”").performClick()
        onNodeWithText("Ancora nessuna", substring = true).assertExists()
        onNodeWithText("Aggiungi una frase").performClick()
        onNodeWithText("Parole").performTextInput("ho preso")
        onNodeWithText("Ne ho usato un po'", substring = true).performClick()
        onNodeWithText("Impara").performClick()
        assertEquals(mapOf("ho preso" to VoiceAction.Use), phrases)
        onNodeWithText("“ho preso”").assertExists()
    }
}
