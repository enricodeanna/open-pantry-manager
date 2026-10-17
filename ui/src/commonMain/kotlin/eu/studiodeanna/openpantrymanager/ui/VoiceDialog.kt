package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import eu.studiodeanna.openpantrymanager.voice.VoiceLanguage

/** Where the voice dialog is: the model to download first, or listening, or what came of it. */
private sealed interface Hearing {
    data object NeedsModel : Hearing
    data class Downloading(val progress: Float) : Hearing
    data class Listening(val partial: String) : Hearing
    data class NotUnderstood(val heard: String?) : Hearing
    data class Failed(val message: String) : Hearing
}

/**
 * Listens to a sentence in [language], also for the [words] of commands and food, and hands what was
 * heard to [onHeard], which does what it says and returns true, or returns false when it makes no
 * sense: then the dialog says so, to try again. The first time, the language's speech model is
 * downloaded, once the user agrees.
 */
@Composable
fun VoiceDialog(
    language: VoiceLanguage,
    voice: VoiceInput,
    words: () -> List<String>,
    onHeard: (List<String>) -> Boolean,
    onDismiss: () -> Unit,
) {
    var hearing by remember { mutableStateOf<Hearing>(Hearing.NeedsModel) }
    // Each run downloads the model if it is missing, then listens; 0 waits for the user to agree to the download.
    var run by remember { mutableIntStateOf(if (voice.hasModel(language)) 1 else 0) }
    DisposableEffect(voice) { onDispose { voice.finish() } }

    LaunchedEffect(run) {
        if (run == 0) return@LaunchedEffect
        try {
            if (!voice.hasModel(language)) {
                hearing = Hearing.Downloading(0f)
                voice.download(language) { hearing = Hearing.Downloading(it) }
            }
            hearing = Hearing.Listening("")
            val heard = voice.listen(language, words()) { hearing = Hearing.Listening(it) }?.filter { it.isNotBlank() }
            // Understood, the caller has done it and closed this dialog.
            if (heard.isNullOrEmpty() || !onHeard(heard)) hearing = Hearing.NotUnderstood(heard?.firstOrNull())
        } catch (e: VoiceFailed) {
            hearing = Hearing.Failed(e.message ?: "")
        }
    }

    val busy = hearing is Hearing.Downloading || hearing is Hearing.Listening
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Mic, contentDescription = null, Modifier.size(32.dp)) },
        title = { Text(strings.voiceCommand) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    is Hearing.NotUnderstood -> Text(
                        now.heard?.let { strings.notUnderstood(it) } ?: strings.heardNothing,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is Hearing.Failed -> Text(now.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            when (hearing) {
                Hearing.NeedsModel -> TextButton(onClick = { run++ }) { Text(strings.download) }
                is Hearing.NotUnderstood, is Hearing.Failed -> TextButton(onClick = { run++ }) { Text(strings.retry) }
                else -> Unit
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (busy) strings.cancel else strings.close) } },
    )
}
