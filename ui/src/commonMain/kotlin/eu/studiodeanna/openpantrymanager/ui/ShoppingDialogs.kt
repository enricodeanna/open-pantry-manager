package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import eu.studiodeanna.openpantrymanager.ListChoiceNeeded
import eu.studiodeanna.openpantrymanager.ShoppingList
import eu.studiodeanna.openpantrymanager.Sublist
import eu.studiodeanna.openpantrymanager.checklist.Item
import eu.studiodeanna.openpantrymanager.checklist.visibleSections
import eu.studiodeanna.openpantrymanager.model.nameKey
import eu.studiodeanna.openpantrymanager.sync.RemoteException
import eu.studiodeanna.openpantrymanager.sync.ShareLink
import eu.studiodeanna.openpantrymanager.sync.SharePasswordRequired
import eu.studiodeanna.openpantrymanager.sync.SharedList
import kotlinx.coroutines.launch

/** Why a food is offered for the shopping list. */
enum class WhyShopping { Finished, LastOpened, Searched }

/**
 * Asks whether to put [name] on the shopping list, as what ([initialText], editable), and in which
 * sublist: the ones the list has, none, or a new one; [suggested] is where it went last time. Items
 * already on the list that may be it ([similar]) are offered instead, ticked or not: picking one puts it
 * back on the list ([onUse]). With [askName] the name is typed here (adding from the shopping list
 * itself). Without a linked list it offers to link one instead.
 */
@Composable
fun ShoppingPromptDialog(
    name: String,
    shopping: ShoppingList?,
    suggested: Sublist?,
    similar: (String) -> List<Item>,
    initialText: String = name,
    askName: Boolean = false,
    /** Why it comes up: [name] is finished, its last closed box opened, or the user asked from a search. */
    why: WhyShopping = WhyShopping.Finished,
    onDismiss: () -> Unit,
    onLink: () -> Unit,
    onAdd: (text: String, sublist: Sublist) -> Unit,
    onUse: (Item) -> Unit,
) {
    if (shopping == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    when (why) {
                        WhyShopping.Finished -> strings.finishedTitle(name)
                        WhyShopping.LastOpened -> strings.lastOpenedTitle(name)
                        WhyShopping.Searched -> strings.noShoppingListTitle
                    },
                )
            },
            text = { Text(if (why == WhyShopping.Searched) strings.noShoppingListHelp else strings.linkToAddFinished) },
            confirmButton = { TextButton(onClick = onLink) { Text(strings.linkShoppingList) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(strings.notNow) } },
        )
        return
    }
    val sections = shopping.doc.visibleSections()
    var text by remember { mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length))) }
    val matches = remember(text.text, shopping.doc) { similar(text.text) }
    // What the user picked; until they do, an item of the very same name is taken.
    var picked by remember { mutableStateOf<String?>(null) }
    var pickedNew by remember { mutableStateOf(false) }
    val use = when {
        pickedNew -> null
        else -> matches.firstOrNull { it.id == picked } ?: matches.firstOrNull { nameKey(it.text) == nameKey(text.text) }
    }
    var choice by remember {
        mutableStateOf(suggested?.takeIf { s -> s !is Sublist.Existing || sections.any { it.id == s.id } } ?: Sublist.None)
    }
    var newName by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val chosen = if (choice is Sublist.New) Sublist.New(newName.trim()) else choice
    val ok = use != null || (text.text.isNotBlank() && (chosen !is Sublist.New || chosen.name.isNotEmpty()))

    fun confirm() {
        when {
            !ok -> Unit
            use != null -> onUse(use)
            else -> onAdd(text.text.trim(), chosen)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (askName) strings.addToShopping else strings.putOnShoppingList(name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!askName) Text(strings.onList(shopping.doc.title), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(strings.nameOnList) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).padding(top = 8.dp, bottom = 4.dp),
                )
                if (askName) LaunchedEffect(Unit) { focus.requestFocus() }
                if (matches.isNotEmpty()) {
                    Text(strings.alreadyOnListHeading, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    matches.forEach { item ->
                        val section = sections.firstOrNull { it.id == item.section }?.name
                        ChoiceRow(strings.listItemChoice(item.text, section, item.done), use?.id == item.id) {
                            picked = item.id
                            pickedNew = false
                        }
                    }
                    ChoiceRow(strings.addAsNew, use == null) {
                        pickedNew = true
                        picked = null
                    }
                }
                if (use == null) {
                    Text(strings.sublist, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    sections.forEach { section ->
                        val label = if (suggested == Sublist.Existing(section.id)) "${section.name} ★" else section.name
                        ChoiceRow(label, choice == Sublist.Existing(section.id)) { choice = Sublist.Existing(section.id) }
                    }
                    ChoiceRow(strings.noSublist, choice == Sublist.None) { choice = Sublist.None }
                    ChoiceRow(strings.newSublist, choice is Sublist.New) { choice = Sublist.New("") }
                    if (choice is Sublist.New) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text(strings.sublistName) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { confirm() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::confirm, enabled = ok) { Text(if (use != null && !use.done) strings.ok else strings.add) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.notNow) } },
    )
}

/**
 * Asks for a share link (an Open Check Lists list's, unless [title] and [intro] say otherwise), then
 * runs [connect]; stays open showing the error if that fails, and calls [onDone] when it returns true
 * (false: another dialog takes over). The password field appears once a share turns out to need one.
 */
@Composable
fun LinkDialog(
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    title: String = strings.linkShoppingList,
    intro: String = strings.linkIntro,
    confirmLabel: String = strings.link,
    connect: suspend (ShareLink) -> Boolean,
) {
    var url by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var askPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val passwordFocus = remember { FocusRequester() }
    val urlFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { urlFocus.requestFocus() }

    fun submit() {
        if (url.isBlank() || busy) return
        busy = true
        error = null
        scope.launch {
            try {
                val address = url.trim().let { if (it.contains("://")) it else "https://$it" }
                if (connect(ShareLink(address, password.ifEmpty { null }))) onDone()
            } catch (e: RemoteException) {
                error = e.message
                if (e is SharePasswordRequired) askPassword = true
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(intro, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(strings.shareLink) },
                    placeholder = { Text("https://cloud.example.com/s/…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = if (askPassword) ImeAction.Next else ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(urlFocus),
                )
                if (askPassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(strings.sharePassword) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus),
                    )
                    LaunchedEffect(Unit) { passwordFocus.requestFocus() }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, enabled = url.isNotBlank() && !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(strings.cancel) } },
    )
}

/** Lets the user pick one of the lists in a shared folder. */
@Composable
fun ListChoiceDialog(choice: ListChoiceNeeded, onDismiss: () -> Unit, open: suspend (fileName: String) -> Unit) =
    FileChoiceDialog(strings.whichList, strings.severalLists, choice.lists, onDismiss, open)

/** Lets the user pick one of the [files] (lists, pantries) in a shared folder. */
@Composable
fun FileChoiceDialog(
    title: String,
    intro: String,
    files: List<SharedList>,
    onDismiss: () -> Unit,
    open: suspend (fileName: String) -> Unit,
) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(intro, style = MaterialTheme.typography.bodyMedium)
                files.forEach { list ->
                    TextButton(
                        onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                try {
                                    open(list.fileName)
                                } catch (e: RemoteException) {
                                    error = e.message
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(list.title, modifier = Modifier.fillMaxWidth())
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(strings.cancel) } },
    )
}

/**
 * Links a shopping list: asks for the link, and for which list when a shared folder holds several.
 * Calls [onDone] once one is linked.
 */
@Composable
fun LinkShoppingFlow(link: suspend (ShareLink) -> Unit, onDismiss: () -> Unit, onDone: () -> Unit) {
    var choosing by remember { mutableStateOf<ListChoiceNeeded?>(null) }
    val pending = choosing
    if (pending == null) {
        LinkDialog(
            onDismiss = onDismiss,
            onDone = onDone,
            connect = { url ->
                try {
                    link(url)
                    true
                } catch (choice: ListChoiceNeeded) {
                    choosing = choice
                    false
                }
            },
        )
    } else {
        ListChoiceDialog(
            choice = pending,
            onDismiss = onDismiss,
            open = { fileName ->
                link(pending.link.copy(fileName = fileName))
                onDone()
            },
        )
    }
}
