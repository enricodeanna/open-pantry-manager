package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.studiodeanna.openpantrymanager.JoinChoiceNeeded
import eu.studiodeanna.openpantrymanager.JoinMode
import eu.studiodeanna.openpantrymanager.PantryChoiceNeeded
import eu.studiodeanna.openpantrymanager.sync.RemoteException
import eu.studiodeanna.openpantrymanager.sync.ShareLink
import kotlinx.coroutines.launch

/**
 * Shares the pantry: asks for the link of a Nextcloud folder (or pantry file), for which pantry when the
 * folder holds several, and, when this phone's pantry joins one that exists, how. [share] does it, as
 * the repository's sharePantry; [onDone] is called once the pantry is shared.
 */
@Composable
fun SharePantryFlow(share: suspend (ShareLink, JoinMode?) -> Unit, onDismiss: () -> Unit, onDone: () -> Unit) {
    var choosing by remember { mutableStateOf<PantryChoiceNeeded?>(null) }
    var joining by remember { mutableStateOf<ShareLink?>(null) }

    /** Shares through [link]; false when another dialog has to ask something first. */
    suspend fun attempt(link: ShareLink): Boolean = try {
        share(link, null)
        true
    } catch (choice: PantryChoiceNeeded) {
        choosing = choice
        false
    } catch (join: JoinChoiceNeeded) {
        joining = join.link
        false
    }

    val join = joining
    val choice = choosing
    when {
        join != null -> JoinDialog(onDismiss) { mode ->
            share(join, mode)
            onDone()
        }
        choice != null -> FileChoiceDialog(strings.whichPantry, strings.severalPantries, choice.pantries, onDismiss) { fileName ->
            if (attempt(choice.link.copy(fileName = fileName))) onDone()
        }
        else -> LinkDialog(
            onDismiss = onDismiss,
            onDone = onDone,
            title = strings.sharePantry,
            intro = strings.sharePantryIntro,
            confirmLabel = strings.share,
            connect = ::attempt,
        )
    }
}

/** Asks how this phone's own pantry joins the shared one: added to it, or replaced by it. */
@Composable
private fun JoinDialog(onDismiss: () -> Unit, join: suspend (JoinMode) -> Unit) {
    var mode by remember { mutableStateOf(JoinMode.Merge) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(strings.joinTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(strings.joinMessage, style = MaterialTheme.typography.bodyMedium)
                ChoiceRow(strings.joinMerge, mode == JoinMode.Merge) { mode = JoinMode.Merge }
                ChoiceRow(strings.joinReplace, mode == JoinMode.Replace) { mode = JoinMode.Replace }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            join(mode)
                        } catch (e: RemoteException) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(strings.share)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(strings.cancel) } },
    )
}
