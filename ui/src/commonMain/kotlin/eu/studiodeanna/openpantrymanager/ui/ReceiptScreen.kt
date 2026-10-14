package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import eu.studiodeanna.openpantrymanager.PantryRepository
import eu.studiodeanna.openpantrymanager.UndoStep
import eu.studiodeanna.openpantrymanager.model.Space
import eu.studiodeanna.openpantrymanager.model.nameKey
import eu.studiodeanna.openpantrymanager.model.visibleSpaces
import eu.studiodeanna.openpantrymanager.receipt.ReceiptEntry
import eu.studiodeanna.openpantrymanager.receipt.ReceiptLine
import eu.studiodeanna.openpantrymanager.receipt.Recognition
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** A receipt line under review: the entry as the user has it so far, and what the app first made of it. */
private data class ReviewRow(
    val key: Int,
    val entry: ReceiptEntry,
    val guessed: String,
    val spacePicked: Boolean = false,
    /** The user has changed something here, so has seen it. */
    val reviewed: Boolean = false,
) {
    /** Still as the app guessed, for a line it did not know for sure, and not looked at yet. */
    val unsure get() = entry.recognition in setOf(Recognition.Unknown, Recognition.Probable) && entry.name == guessed && !reviewed
    val ready get() = !entry.include || (entry.name.isNotBlank() && entry.space != null)
}

/**
 * What was read on a receipt, for the user to check before anything is saved: each line's food,
 * where it goes, how many and until when. Lines the app is unsure about come first and say so; lines
 * left out before come last, left out again. Saving puts the food away and teaches the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScreen(
    repository: PantryRepository,
    entries: List<ReceiptEntry>,
    onBack: () -> Unit,
    onSaved: (count: Int, undo: UndoStep?) -> Unit,
) {
    val doc by repository.pantry.collectAsState()
    val spaces = remember(doc) { doc.visibleSpaces() }
    val today = repository.today()
    val rows = remember {
        val order = compareBy<ReceiptEntry>(
            { it.recognition == Recognition.Skipped },
            { it.recognition == Recognition.Known && it.space != null },
        )
        mutableStateListOf(*entries.sortedWith(order).mapIndexed { i, e -> ReviewRow(i, e, e.name) }.toTypedArray())
    }
    var nextKey by remember { mutableStateOf(entries.size) }
    /** The row a new space is being named for. */
    var newSpaceFor by remember { mutableStateOf<Int?>(null) }
    val needHelp = rows.count { it.entry.include && (it.unsure || it.entry.space == null) }
    val canSave = rows.all { it.ready } && rows.any { it.entry.include }

    fun update(index: Int, change: (ReviewRow) -> ReviewRow) {
        rows[index] = change(rows[index]).copy(reviewed = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AppIcons.Close, contentDescription = strings.discardReceipt) }
                },
                title = { Text(strings.receipt) },
                actions = {
                    TextButton(
                        onClick = {
                            val (step, count) = repository.saveReceipt(rows.map { it.entry })
                            onSaved(count, step)
                        },
                        enabled = canSave,
                    ) { Text(strings.save, fontWeight = FontWeight.SemiBold) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "summary") {
                    Text(
                        strings.receiptSummary(rows.count { it.entry.include }, needHelp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                    ReceiptRowCard(
                        row = row,
                        spaces = spaces,
                        today = today,
                        repository = repository,
                        onChange = { change -> update(index) { change(it) } },
                        onNewSpace = { newSpaceFor = row.key },
                    )
                }
                item(key = "add") {
                    OutlinedButton(onClick = {
                        rows += ReviewRow(nextKey++, ReceiptEntry(ReceiptLine(""), "", Recognition.Known, 1, null, null, true), "")
                    }) {
                        Icon(AppIcons.Add, contentDescription = null)
                        Text(strings.addMissing, Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    newSpaceFor?.let { key ->
        AddSpaceDialog(spaces.map { it.name }, onDismiss = { newSpaceFor = null }) { name ->
            newSpaceFor = null
            // The space is the pantry's at once, for the other foods too, whether or not the receipt is saved.
            val space = repository.addSpace(name)
            val index = rows.indexOfFirst { it.key == key }
            if (index >= 0) update(index) { it.copy(entry = it.entry.copy(space = space), spacePicked = true) }
        }
    }
}

@Composable
private fun ReceiptRowCard(
    row: ReviewRow,
    spaces: List<Space>,
    today: LocalDate,
    repository: PantryRepository,
    onChange: ((ReviewRow) -> ReviewRow) -> Unit,
    /** Names a space the pantry does not have yet, for this food to go in. */
    onNewSpace: () -> Unit,
) {
    val entry = row.entry
    val needsHelp = entry.include && (row.unsure || entry.space == null)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (needsHelp) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = entry.include,
                    onCheckedChange = { include -> onChange { it.copy(entry = it.entry.copy(include = include)) } },
                )
                Column(Modifier.weight(1f)) {
                    if (entry.line.text.isNotEmpty()) {
                        Text(
                            entry.line.text + (entry.line.price?.let { "  " + formatPrice(it) } ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val note = when {
                        !entry.include && entry.recognition == Recognition.Skipped -> strings.leftOutBefore
                        !entry.include -> strings.leftOut
                        row.unsure && entry.recognition == Recognition.Unknown -> strings.whatIsThis
                        row.unsure -> strings.isThisRight
                        entry.space == null -> strings.whereDoesItGo
                        else -> null
                    }
                    note?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (entry.include) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!entry.include) return@Column
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var editing by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = entry.name,
                    onValueChange = { name ->
                        editing = true
                        onChange {
                            val space = if (it.spacePicked) it.entry.space else repository.placeFor(name) ?: it.entry.space
                            it.copy(entry = it.entry.copy(name = name, space = space))
                        }
                    },
                    label = { Text(strings.food) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (editing) {
                    val suggestions = remember(entry.name) {
                        repository.complete(entry.name).filterNot { nameKey(it.name) == nameKey(entry.name) }.take(3)
                    }
                    suggestions.forEach { food ->
                        Text(
                            food.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable {
                                editing = false
                                onChange {
                                    val space = if (it.spacePicked) it.entry.space else repository.placeFor(food.name) ?: it.entry.space
                                    val keeps = food.keepsDays?.let { days -> today.plus(DatePeriod(days = days)) }
                                    it.copy(entry = it.entry.copy(name = food.name, space = space, expires = it.entry.expires ?: keeps))
                                }
                            }.padding(vertical = 6.dp),
                        )
                    }
                }
                SpaceChips(spaces, entry.space, usual = repository.placeFor(entry.name), onNewSpace = onNewSpace) { space ->
                    onChange { it.copy(entry = it.entry.copy(space = space), spacePicked = true) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QuantityStepper(entry.quantity, { q -> onChange { it.copy(entry = it.entry.copy(quantity = q)) } })
                    Box(Modifier.weight(1f).padding(start = 8.dp)) {
                        ExpiryField(entry.expires, today, repository.food(entry.name)?.keepsDays, compact = true) { date ->
                            onChange { it.copy(entry = it.entry.copy(expires = date)) }
                        }
                    }
                }
            }
        }
    }
}

/** "1,15", as Italian receipts print it. */
private fun formatPrice(price: Double): String {
    val cents = kotlin.math.round(price * 100).toLong()
    return "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"
}
