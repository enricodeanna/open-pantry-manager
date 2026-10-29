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
import androidx.compose.runtime.key
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
import eu.studiodeanna.openpantrymanager.costs.Measure
import eu.studiodeanna.openpantrymanager.costs.MeasureUnit
import kotlinx.datetime.LocalDate

/**
 * Puts food in the pantry. As the name is typed, known foods are offered, and the space the food
 * usually goes in is chosen, and whether it comes in boxes, until the user picks themselves.
 * [initialSpace] starts on a space; [initialName] and [initialQuantity] fill in what a voice command said.
 */
@Composable
fun AddFoodDialog(
    repository: PantryRepository,
    spaces: List<Space>,
    initialSpace: String? = null,
    initialName: String = "",
    initialQuantity: Int = 1,
    onDismiss: () -> Unit,
    onAdd: (food: NewFood) -> Unit,
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
    var boxedPicked by remember { mutableStateOf<Boolean?>(null) }
    val boxed = boxedPicked ?: (food?.boxed == true)
    var size by remember { mutableStateOf<Measure?>(null) }
    var price by remember { mutableStateOf<Long?>(null) }
    var shop by remember { mutableStateOf("") }
    val suggestions = remember(name) { repository.complete(name).filterNot { nameKey(it.name) == nameKey(name) }.take(4) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(usual) { if (!spacePicked && usual != null) space = usual }
    val chosen = space?.takeIf { id -> spaces.any { it.id == id } }
    val ok = name.isNotBlank() && chosen != null
    fun add() {
        if (ok) onAdd(NewFood(name.trim(), chosen, quantity, expires, bought, boxed, (size ?: food?.size).takeIf { boxed }, price, shop.trim().takeIf { it.isNotEmpty() }))
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
                BoxedField(boxed) { boxedPicked = it }
                if (boxed) {
                    // As the food came last time, until changed; keyed so it follows the name typed.
                    key(food?.size) { SizeField(food?.size, strings.packageSize, Modifier.fillMaxWidth()) { size = it } }
                }
                BoughtField(bought, today) { bought = it }
                ExpiryField(expires, today, food?.keepsDays, from = bought ?: today) { expires = it }
                // What it cost goes in the costs, not in the pantry's lists.
                MoneyField(price, strings.pricePaid, Modifier.fillMaxWidth()) { price = it }
                val each = price?.takeIf { quantity > 1 }
                if (each != null) {
                    Text(strings.eachCosts(strings.money(each / quantity)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (price != null) {
                    OutlinedTextField(
                        value = shop,
                        onValueChange = { shop = it },
                        label = { Text(strings.shop) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            LaunchedEffect(Unit) { focus.requestFocus() }
        },
        confirmButton = { TextButton(onClick = ::add, enabled = ok) { Text(strings.add) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

/** Food to put in the pantry, as the add dialog has it: what, where, how many, until when, and what it cost. */
data class NewFood(
    val name: String,
    val space: String,
    val quantity: Int,
    val expires: LocalDate?,
    val bought: LocalDate?,
    val boxed: Boolean,
    val size: Measure?,
    /** In cents, for all of them. */
    val price: Long?,
    val shop: String?,
)

/**
 * Changes an item, or takes it out: finished (which may offer the shopping list) or deleted. Food in
 * boxes can be marked opened (today, unless it was already) or closed again.
 */
@Composable
fun ItemDialog(
    item: PantryItem,
    spaces: List<Space>,
    today: LocalDate,
    keepsDays: Int?,
    onDismiss: () -> Unit,
    onSave: (name: String, quantity: Int, expires: LocalDate?, space: String, bought: LocalDate?, boxed: Boolean, opened: LocalDate?, size: Measure?, left: Double?) -> Unit,
    onFinished: () -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(item.name) }
    var quantity by remember { mutableStateOf(item.quantity) }
    var expires by remember { mutableStateOf(item.expires) }
    var space by remember { mutableStateOf(item.space) }
    var bought by remember { mutableStateOf(item.bought) }
    var boxed by remember { mutableStateOf(item.boxed) }
    var opened by remember { mutableStateOf(item.opened) }
    var size by remember { mutableStateOf(item.size) }
    var left by remember { mutableStateOf(item.left) }
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
                BoxedField(boxed) { boxed = it }
                if (boxed) {
                    SizeField(item.size, strings.packageSize, Modifier.fillMaxWidth()) { size = it }
                    CheckRow(opened?.let { "${strings.boxOpened} · ${strings.shortDate(it, today)}" } ?: strings.boxOpened, opened != null) {
                        opened = if (it) item.opened ?: today else null
                    }
                    val unit = size?.unit
                    if (opened != null && unit != null) {
                        AmountField(item.left ?: size?.let { it.value * quantity }, unit, strings.leftInBox, Modifier.fillMaxWidth()) { left = it }
                    }
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
            TextButton(
                onClick = { onSave(name, quantity, expires, space, bought, boxed, opened.takeIf { boxed }, size.takeIf { boxed }, left) },
                enabled = name.isNotBlank(),
            ) { Text(strings.save) }
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

/** Whether food comes in boxes or packets, which are opened before they are finished. */
@Composable
private fun BoxedField(boxed: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        CheckRow(strings.inBoxes, boxed, onChange)
        if (boxed) {
            Text(
                strings.inBoxesHelp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 48.dp),
            )
        }
    }
}

/**
 * How much of an opened box of [size] was used: the usual amount ([usual], learned), a few round ones
 * below what is left, the rest of the box, or any amount typed. [opening]: the box was opened just now,
 * and leaving says it was only opened.
 */
@Composable
fun AmountDialog(item: PantryItem, size: Measure, usual: Double?, opening: Boolean, onDismiss: () -> Unit, onUse: (Double) -> Unit) {
    val left = item.left ?: (size.value * item.quantity)
    // Of boxes counted together, the rest of the one in use.
    val rest = (left - (item.quantity - 1) * size.value).coerceAtLeast(0.0)
    val steps = (listOfNotNull(usual) + ROUND_AMOUNTS.getValue(size.unit)).distinct().filter { it < rest }
    var typed by remember { mutableStateOf<Double?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.howMuchUsed, style = MaterialTheme.typography.bodyLarge)
                Text(
                    strings.amountLeft(strings.measure(Measure(left, size.unit))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    steps.forEach { amount ->
                        val label = strings.measure(Measure(amount, size.unit))
                        FilterChip(selected = false, onClick = { onUse(amount) }, label = { Text(if (amount == usual) strings.asUsual(label) else label) })
                    }
                    FilterChip(selected = false, onClick = { onUse(rest) }, label = { Text(strings.theRest) })
                }
                AmountField(null, size.unit, strings.howMuchUsed, Modifier.fillMaxWidth()) { typed = it?.takeIf { amount -> amount > 0 } }
            }
        },
        confirmButton = {
            TextButton(onClick = { typed?.let(onUse) }, enabled = typed != null) { Text(strings.use) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (opening) strings.justOpened else strings.cancel) } },
    )
}

/** Amounts offered at a tap, by unit: what a serving often is. */
private val ROUND_AMOUNTS = mapOf(
    MeasureUnit.Gram to listOf(50.0, 100.0, 200.0),
    MeasureUnit.Millilitre to listOf(50.0, 100.0, 250.0),
)
