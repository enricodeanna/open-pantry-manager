package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import eu.studiodeanna.openpantrymanager.voice.Italian
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
                            (sentence.first() != "che tempo fa").also { if (it) open = false }
                        },
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
}
