package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import eu.studiodeanna.openpantrymanager.voice.VoiceAction
import eu.studiodeanna.openpantrymanager.voice.VoiceLanguage

/** Where the voice dialog is: the model to download first, or listening, or what came of it. */
private sealed interface Hearing {
    data object NeedsModel : Hearing
    data class Downloading(val progress: Float) : Hearing
    data class Listening(val partial: String) : Hearing
    /** [heard] (empty: nothing) made no sense; [phrase], words of it the user could teach the app. */
    data class NotUnderstood(val heard: List<String>, val phrase: String?) : Hearing
    /** The user teaches the app what words of [heard] mean, starting from [phrase]. */
    data class Teaching(val heard: List<String>, val phrase: String) : Hearing
    /** [heard] makes sense only as [said]: the user says whether that is what they said. */
    data class Checking(val heard: List<String>, val said: String) : Hearing
    data class Failed(val message: String) : Hearing
}

/** What came of a sentence heard, for the voice dialog. */
sealed interface Obeyed {
    /** Done, or handed on to another dialog: the voice dialog is closed. */
    data object Done : Obeyed

    /** It makes no sense. */
    data object NotUnderstood : Obeyed

    /** It makes sense only as [heard], which is done if the user says that is what they said. */
    data class IfSaid(val heard: String) : Obeyed
}

/**
 * Listens to a sentence in [language], also for the [words] of commands and food, and hands what was
 * heard to [onHeard], which does what it says, or says it makes no sense, or what to check with the
 * user first. Not understood, the dialog says so, to try again, or to teach the app the words it did
 * not know, as [teach] finds them; [onLearn] learns what the user says they mean, and the sentence is
 * tried again. The first time, the language's speech model is downloaded, once the user agrees.
 */
@Composable
fun VoiceDialog(
    language: VoiceLanguage,
    voice: VoiceInput,
    words: () -> List<String>,
    onHeard: (List<String>) -> Obeyed,
    teach: (String) -> String?,
    onLearn: (phrase: String, action: VoiceAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var hearing by remember { mutableStateOf<Hearing>(Hearing.NeedsModel) }
    // Each run downloads the model if it is missing, then listens; 0 waits for the user to agree to the download.
    var run by remember { mutableIntStateOf(if (voice.hasModel(language)) 1 else 0) }
    var phrase by remember { mutableStateOf("") }
    var action by remember { mutableStateOf<VoiceAction?>(null) }
    DisposableEffect(voice) { onDispose { voice.finish() } }

    // What was heard freely, first, holds every word said, the ones to teach too.
    fun notUnderstood(heard: List<String>) = Hearing.NotUnderstood(heard, heard.firstOrNull()?.takeIf { it.isNotBlank() }?.let(teach))

    // Done, the caller has closed this dialog.
    fun obey(heard: List<String>) {
        when (val outcome = if (heard.all { it.isBlank() }) Obeyed.NotUnderstood else onHeard(heard)) {
            Obeyed.Done -> Unit
            Obeyed.NotUnderstood -> hearing = notUnderstood(heard)
            is Obeyed.IfSaid -> hearing = Hearing.Checking(heard, outcome.heard)
        }
    }

    LaunchedEffect(run) {
        if (run == 0) return@LaunchedEffect
        try {
            if (!voice.hasModel(language)) {
                hearing = Hearing.Downloading(0f)
                voice.download(language) { hearing = Hearing.Downloading(it) }
            }
            hearing = Hearing.Listening("")
            obey(voice.listen(language, words()) { hearing = Hearing.Listening(it) }.orEmpty())
        } catch (e: VoiceFailed) {
            hearing = Hearing.Failed(e.message ?: "")
        }
    }

    val busy = hearing is Hearing.Downloading || hearing is Hearing.Listening
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Mic, contentDescription = null, Modifier.size(32.dp)) },
        title = { Text(if (hearing is Hearing.Teaching) strings.teachTitle else strings.voiceCommand) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val now = hearing) {
                    Hearing.NeedsModel -> Text(strings.voiceModelNeeded((language.model.bytes / 1_000_000).toInt()), style = MaterialTheme.typography.bodyMedium)
                    is Hearing.Downloading -> {
                        Text(strings.downloading((now.progress * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { now.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    is Hearing.Listening -> {
                        Text(now.partial.ifBlank { strings.listening }, style = MaterialTheme.typography.titleMedium)
                        Text(strings.sayForExample, style = MaterialTheme.typography.labelLarge)
                        language.examples.forEach {
                            Text("“$it”", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                        }
                    }
                    is Hearing.NotUnderstood -> {
                        Text(
                            now.heard.firstOrNull { it.isNotBlank() }?.let { strings.notUnderstood(it) } ?: strings.heardNothing,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (now.phrase != null) Text(strings.canTeach, style = MaterialTheme.typography.bodyMedium)
                    }
                    is Hearing.Teaching -> {
                        Text(strings.teachIntro(now.heard.first { it.isNotBlank() }), style = MaterialTheme.typography.bodyMedium)
                        PhraseForm(phrase, { phrase = it }, action) { action = it }
                        Text(strings.teachNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is Hearing.Checking -> Text(strings.didYouSay(now.said), style = MaterialTheme.typography.bodyLarge)
                    is Hearing.Failed -> Text(now.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            when (val now = hearing) {
                Hearing.NeedsModel -> TextButton(onClick = { run++ }) { Text(strings.download) }
                is Hearing.NotUnderstood -> Row {
                    if (now.phrase != null) {
                        TextButton(onClick = {
                            phrase = now.phrase
                            action = null
                            hearing = Hearing.Teaching(now.heard, now.phrase)
                        }) { Text(strings.teachMe) }
                    }
                    TextButton(onClick = { run++ }) { Text(strings.retry) }
                }
                is Hearing.Teaching -> TextButton(
                    enabled = action != null && phrase.isNotBlank(),
                    onClick = {
                        onLearn(phrase, action ?: return@TextButton)
                        obey(now.heard)
                    },
                ) { Text(strings.learn) }
                is Hearing.Checking -> TextButton(onClick = {
                    // As said, it is understood as it is.
                    if (onHeard(listOf(now.said)) != Obeyed.Done) hearing = notUnderstood(now.heard)
                }) { Text(strings.yes) }
                is Hearing.Failed -> TextButton(onClick = { run++ }) { Text(strings.retry) }
                else -> Unit
            }
        },
        dismissButton = {
            when (val now = hearing) {
                is Hearing.Teaching -> TextButton(onClick = { hearing = Hearing.NotUnderstood(now.heard, now.phrase) }) { Text(strings.cancel) }
                is Hearing.Checking -> TextButton(onClick = { hearing = notUnderstood(now.heard) }) { Text(strings.no) }
                else -> TextButton(onClick = onDismiss) { Text(if (busy) strings.cancel else strings.close) }
            }
        },
    )
}

/** The words of a phrase to teach, [phrase], and which of the things voice commands do it means, [action]. */
@Composable
private fun PhraseForm(phrase: String, onPhrase: (String) -> Unit, action: VoiceAction?, onAction: (VoiceAction) -> Unit) {
    Column {
        OutlinedTextField(
            value = phrase,
            onValueChange = onPhrase,
            label = { Text(strings.phraseWords) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(strings.phraseMeans, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
        VoiceAction.entries.forEach { choice -> ChoiceRow(strings.meaning(choice), action == choice) { onAction(choice) } }
    }
}

/**
 * The phrases taught to voice commands in this language ([phrases], each with what it means), to
 * forget ([onForget]) or to add to ([onLearn]) without a sentence first.
 */
@Composable
fun TaughtPhrasesDialog(
    phrases: Map<String, VoiceAction>,
    onLearn: (phrase: String, action: VoiceAction) -> Unit,
    onForget: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf("") }
    var action by remember { mutableStateOf<VoiceAction?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (adding) strings.addPhrase else strings.taughtPhrasesTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (adding) {
                    PhraseForm(phrase, { phrase = it }, action) { action = it }
                    Text(strings.teachNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    if (phrases.isEmpty()) Text(strings.noTaughtPhrases, style = MaterialTheme.typography.bodyMedium)
                    phrases.entries.sortedBy { it.key }.forEach { (text, means) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("“$text”", style = MaterialTheme.typography.bodyLarge)
                                Text(strings.meaning(means), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onForget(text) }) { Icon(AppIcons.Close, contentDescription = strings.forget(text)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (adding) {
                TextButton(
                    enabled = action != null && phrase.isNotBlank(),
                    onClick = {
                        onLearn(phrase, action ?: return@TextButton)
                        adding = false
                    },
                ) { Text(strings.learn) }
            } else {
                TextButton(onClick = {
                    phrase = ""
                    action = null
                    adding = true
                }) { Text(strings.addPhrase) }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (adding) adding = false else onDismiss() }) { Text(if (adding) strings.cancel else strings.close) }
        },
    )
}
