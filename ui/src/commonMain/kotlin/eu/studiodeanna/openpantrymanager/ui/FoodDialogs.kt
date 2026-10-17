package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import eu.studiodeanna.openpantrymanager.PantryRepository
import eu.studiodeanna.openpantrymanager.model.PantryItem
import eu.studiodeanna.openpantrymanager.model.Space
import eu.studiodeanna.openpantrymanager.model.nameKey
import kotlinx.datetime.LocalDate

/**
 * Puts food in the pantry. As the name is typed, known foods are offered, and the space the food
 * usually goes in is chosen, until the user picks one themselves. [initialSpace] starts on a space;
 * [initialName] and [initialQuantity] fill in what a voice command said.
 */
@Composable
fun AddFoodDialog(
    repository: PantryRepository,
    spaces: List<Space>,
    initialSpace: String? = null,
    initialName: String = "",
    initialQuantity: Int = 1,
    onDismiss: () -> Unit,
    onAdd: (name: String, space: String, quantity: Int, expires: LocalDate?, bought: LocalDate?) -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length))) }
    val name = field.text
    var space by remember { mutableStateOf(initialSpace) }
    var spacePicked by remember { mutableStateOf(initialSpace != null) }
    var quantity by remember { mutableStateOf(initialQuantity) }
    var expires by remember { mutableStateOf<LocalDate?>(null) }
    val today = repository.today()
    var bought by remember { mutableStateOf<LocalDate?>(today) }
    val usual = remember(name) { repository.placeFor(name) }
    val food = remember(name) { repository.food(name) }
    val suggestions = remember(name) { repository.complete(name).filterNot { nameKey(it.name) == nameKey(name) }.take(4) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(usual) { if (!spacePicked && usual != null) space = usual }
    val chosen = space?.takeIf { id -> spaces.any { it.id == id } }
    val ok = name.isNotBlank() && chosen != null
    fun add() {
        if (ok) onAdd(name.trim(), chosen, quantity, expires, bought)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addFood) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    label = { Text(strings.food) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (suggestions.isNotEmpty()) {
                    Column {
                        suggestions.forEach { suggestion ->
                            val place = repository.placeFor(suggestion.name)?.let { id -> spaces.firstOrNull { it.id == id }?.name }
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    field = TextFieldValue(suggestion.name, TextRange(suggestion.name.length))
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(suggestion.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                place?.let {
                                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Text(strings.where, style = MaterialTheme.typography.labelLarge)
                SpaceChips(spaces, chosen, usual) {
                    space = it
                    spacePicked = true
                }
                if (usual != null && !spacePicked) {
                    Text(
                        strings.usuallyIn(spaces.first { it.id == usual }.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.howMany, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    QuantityStepper(quantity, { quantity = it })
                }
                BoughtField(bought, today) { bought = it }
                ExpiryField(expires, today, food?.keepsDays, from = bought ?: today) { expires = it }
            }
            LaunchedEffect(Unit) { focus.requestFocus() }
        },
        confirmButton = { TextButton(onClick = ::add, enabled = ok) { Text(strings.add) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

/** Changes an item, or takes it out: finished (which may offer the shopping list) or deleted. */
@Composable
fun ItemDialog(
    item: PantryItem,
    spaces: List<Space>,
    today: LocalDate,
    keepsDays: Int?,
    onDismiss: () -> Unit,
    onSave: (name: String, quantity: Int, expires: LocalDate?, space: String, bought: LocalDate?) -> Unit,
    onFinished: () -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(item.name) }
    var quantity by remember { mutableStateOf(item.quantity) }
    var expires by remember { mutableStateOf(item.expires) }
    var space by remember { mutableStateOf(item.space) }
    var bought by remember { mutableStateOf(item.bought) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.editFood) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.food) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(strings.where, style = MaterialTheme.typography.labelLarge)
                SpaceChips(spaces, space, usual = null) { space = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.howMany, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    QuantityStepper(quantity, { quantity = it })
                }
                BoughtField(bought, today) { bought = it }
                ExpiryField(expires, today, keepsDays, from = bought ?: today) { expires = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onFinished) { Text(strings.finished, fontWeight = FontWeight.SemiBold) }
                    TextButton(onClick = onDelete) { Text(strings.delete, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, quantity, expires, space, bought) }, enabled = name.isNotBlank()) { Text(strings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

/**
 * Moves some or all of an item to another space. The space it is probably going to ([suggested],
 * from what the user did before) is chosen to start with.
 */
@Composable
fun MoveDialog(
    item: PantryItem,
    spaces: List<Space>,
    suggested: String?,
    onDismiss: () -> Unit,
    onMove: (space: String, count: Int) -> Unit,
) {
    val targets = spaces.filter { it.id != item.space }
    var target by remember { mutableStateOf(suggested?.takeIf { id -> targets.any { it.id == id } }) }
    var count by remember { mutableStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.moveTitle(item.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (targets.isEmpty()) {
                    Text(strings.noOtherSpace, style = MaterialTheme.typography.bodyMedium)
                }
                targets.forEach { space ->
                    ChoiceRow(if (space.id == suggested) "${space.name} ★" else space.name, target == space.id) { target = space.id }
                }
                if (suggested != null && targets.any { it.id == suggested }) {
                    Text(strings.starMeansUsual, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (item.quantity > 1) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(strings.howMany, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        QuantityStepper(count, { count = it }, max = item.quantity)
                    }
                    FilterChip(
                        selected = count == item.quantity,
                        onClick = { count = item.quantity },
                        label = { Text(strings.allOf(item.quantity)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { target?.let { onMove(it, count) } }, enabled = target != null) { Text(strings.move) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}
