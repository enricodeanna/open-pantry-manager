package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.studiodeanna.openpantrymanager.PantrySharing
import eu.studiodeanna.openpantrymanager.ShoppingList
import eu.studiodeanna.openpantrymanager.SyncState
import eu.studiodeanna.openpantrymanager.model.Space
import eu.studiodeanna.openpantrymanager.store.LanguageChoice
import eu.studiodeanna.openpantrymanager.store.Settings
import eu.studiodeanna.openpantrymanager.store.ThemeChoice
import eu.studiodeanna.openpantrymanager.sync.ShareLinks
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@Composable
fun TextDialog(
    title: String,
    label: String,
    confirm: String,
    initial: String = "",
    onDismiss: () -> Unit,
    message: String? = null,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    val ok = value.text.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (ok) onConfirm(value.text) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            LaunchedEffect(Unit) { focus.requestFocus() }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.text) }, enabled = ok) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

/**
 * The spaces as chips to pick one from; [usual] is marked as where the food usually goes. With
 * [onNewSpace], a last chip names a space the pantry does not have yet.
 */
@Composable
fun SpaceChips(spaces: List<Space>, selected: String?, usual: String?, onNewSpace: (() -> Unit)? = null, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        spaces.forEach { space ->
            FilterChip(
                selected = selected == space.id,
                onClick = { onSelect(space.id) },
                label = { Text(if (space.id == usual) "${space.name} ★" else space.name) },
            )
        }
        if (onNewSpace != null) {
            AssistChip(
                onClick = onNewSpace,
                label = { Text(strings.newSpace) },
                leadingIcon = { Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

/** − n + for how many; never below one, nor above [max]. */
@Composable
fun QuantityStepper(value: Int, onChange: (Int) -> Unit, max: Int = 999) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(value - 1) }, enabled = value > 1) {
            Icon(AppIcons.Remove, contentDescription = strings.fewer)
        }
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 32.dp),
        )
        IconButton(onClick = { onChange(value + 1) }, enabled = value < max) {
            Icon(AppIcons.Add, contentDescription = strings.more)
        }
    }
}

/**
 * The expiry day of some food: none, a day picked in a calendar, or, for food whose keeping time
 * the app has learned ([keepsDays]), that many days from the day it was bought ([from]) in one tap.
 * [compact] leaves out the heading and the "no date" chip, for lists of many items; there a set date
 * has a clear button.
 */
@Composable
fun ExpiryField(
    value: LocalDate?,
    today: LocalDate,
    keepsDays: Int?,
    from: LocalDate = today,
    compact: Boolean = false,
    onChange: (LocalDate?) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val learned = keepsDays?.let { from.plus(DatePeriod(days = it)) }
    val chips = @Composable {
        if (!compact) FilterChip(selected = value == null, onClick = { onChange(null) }, label = { Text(strings.noDate) })
        if (learned != null && learned != value) {
            AssistChip(onClick = { onChange(learned) }, label = { Text(strings.usuallyKeeps(strings.shortDate(learned, today))) })
        }
        if (value != null) {
            InputChip(
                selected = true,
                onClick = { picking = true },
                label = { Text(if (compact) strings.expiresOn(strings.shortDate(value, today)) else strings.shortDate(value, today)) },
                leadingIcon = { Icon(AppIcons.Event, contentDescription = null, Modifier.size(18.dp)) },
                trailingIcon = if (compact) {
                    { Icon(AppIcons.Close, contentDescription = strings.noDate, Modifier.size(18.dp).clickable { onChange(null) }) }
                } else {
                    null
                },
            )
        } else {
            AssistChip(
                onClick = { picking = true },
                label = { Text(if (compact) strings.expires else strings.pickDate) },
                leadingIcon = { Icon(AppIcons.Event, contentDescription = null, Modifier.size(18.dp)) },
            )
        }
    }
    if (compact) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(strings.expires, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
        }
    }
    if (picking) PickDateDialog(value ?: learned ?: today, onDismiss = { picking = false }) { onChange(it) }
}

/**
 * The day some food was bought: today unless the user says otherwise, another day picked in a
 * calendar, or not known.
 */
@Composable
fun BoughtField(value: LocalDate?, today: LocalDate, onChange: (LocalDate?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(strings.bought, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = value == null, onClick = { onChange(null) }, label = { Text(strings.noDate) })
            FilterChip(selected = value == today, onClick = { onChange(today) }, label = { Text(strings.today) })
            if (value != null && value != today) {
                InputChip(
                    selected = true,
                    onClick = { picking = true },
                    label = { Text(strings.shortDate(value, today)) },
                    leadingIcon = { Icon(AppIcons.Event, contentDescription = null, Modifier.size(18.dp)) },
                )
            } else {
                AssistChip(
                    onClick = { picking = true },
                    label = { Text(strings.anotherDay) },
                    leadingIcon = { Icon(AppIcons.Event, contentDescription = null, Modifier.size(18.dp)) },
                )
            }
        }
    }
    if (picking) PickDateDialog(value ?: today, onDismiss = { picking = false }) { onChange(it) }
}

/** A calendar to pick a day in, starting on [initial]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickDateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toPickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                state.selectedDateMillis?.let { onPick(pickerMillisToDate(it)) }
            }) { Text(strings.ok) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    ) {
        DatePicker(state = state, showModeToggle = true)
    }
}

/**
 * Names a new space; the usual ones not in the pantry yet are a tap away.
 */
@Composable
fun AddSpaceDialog(taken: Collection<String>, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val suggestions = strings.commonSpaces.filter { name -> taken.none { it.equals(name, ignoreCase = true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addSpace) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(strings.spaceNameHint) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) onAdd(value) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { name -> AssistChip(onClick = { onAdd(name) }, label = { Text(name) }) }
                    }
                }
            }
            LaunchedEffect(Unit) { focus.requestFocus() }
        },
        confirmButton = { TextButton(onClick = { onAdd(value) }, enabled = value.isNotBlank()) { Text(strings.add) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

/**
 * Whether the pantry is shared, theme, language, whether finished food is offered for the shopping
 * list, and which list that is; each change applies and is saved at once.
 */
@Composable
fun SettingsDialog(
    settings: Settings,
    shopping: ShoppingList?,
    sharing: PantrySharing?,
    onChange: (Settings) -> Unit,
    onLinkShopping: () -> Unit,
    onUnlinkShopping: () -> Unit,
    onSharePantry: () -> Unit,
    onStopSharing: () -> Unit,
    onSyncNow: () -> Unit,
    /** How many phrases were taught to voice commands; null where there are none. */
    taughtPhrases: Int?,
    onTaughtPhrases: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.settings) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(strings.sharedPantry, style = MaterialTheme.typography.labelLarge)
                if (sharing == null) {
                    Text(strings.notShared, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onSharePantry) { Text(strings.sharePantry) }
                } else {
                    Text(strings.sharedAs(sharing.link.fileName.orEmpty()), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        describe(sharing.sync, strings),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (sharing.sync is SyncState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(strings.addressForOthers, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    SelectionContainer {
                        Text(ShareLinks.shareable(sharing.link), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    }
                    FlowRow {
                        TextButton(onClick = onSyncNow) { Text(strings.syncNow) }
                        TextButton(onClick = onStopSharing) { Text(strings.stopSharing, color = MaterialTheme.colorScheme.error) }
                    }
                }
                Text(strings.shoppingList, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                if (shopping == null) {
                    Text(strings.noShoppingList, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onLinkShopping) { Text(strings.linkShoppingList) }
                } else {
                    Text(strings.linkedTo(shopping.doc.title), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onUnlinkShopping) { Text(strings.unlink, color = MaterialTheme.colorScheme.error) }
                }
                Row(
                    Modifier.fillMaxWidth().toggleable(
                        value = settings.offerShopping,
                        role = Role.Checkbox,
                        onValueChange = { onChange(settings.copy(offerShopping = it)) },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = settings.offerShopping, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                    Text(strings.offerShopping, style = MaterialTheme.typography.bodyLarge)
                }
                if (taughtPhrases != null) {
                    Text(strings.voiceCommands, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                    Text(strings.taughtPhrases(taughtPhrases), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onTaughtPhrases) { Text(strings.taughtPhrasesTitle) }
                }
                Text(strings.theme, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                ThemeChoice.entries.forEach { choice ->
                    val label = when (choice) {
                        ThemeChoice.System -> strings.systemDefault
                        ThemeChoice.Light -> strings.light
                        ThemeChoice.Dark -> strings.dark
                    }
                    ChoiceRow(label, settings.theme == choice) { onChange(settings.copy(theme = choice)) }
                }
                Text(strings.language, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                LanguageChoice.entries.forEach { choice ->
                    // Languages are named in themselves, so each can be found whatever is shown now.
                    val label = when (choice) {
                        LanguageChoice.System -> strings.systemDefault
                        LanguageChoice.English -> "English"
                        LanguageChoice.Italian -> "Italiano"
                    }
                    ChoiceRow(label, settings.language == choice) { onChange(settings.copy(language = choice)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.close) } },
    )
}

@Composable
fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
