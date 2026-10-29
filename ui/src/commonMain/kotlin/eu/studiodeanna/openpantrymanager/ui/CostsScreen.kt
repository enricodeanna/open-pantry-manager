package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.studiodeanna.openpantrymanager.PantryRepository
import eu.studiodeanna.openpantrymanager.UndoStep
import eu.studiodeanna.openpantrymanager.checklist.visibleSections
import eu.studiodeanna.openpantrymanager.costs.CostGrouping
import eu.studiodeanna.openpantrymanager.costs.CostLine
import eu.studiodeanna.openpantrymanager.costs.Measure
import eu.studiodeanna.openpantrymanager.costs.Purchase
import eu.studiodeanna.openpantrymanager.costs.costLines
import eu.studiodeanna.openpantrymanager.costs.knownGroups
import eu.studiodeanna.openpantrymanager.memory.food
import eu.studiodeanna.openpantrymanager.memory.groupOf
import eu.studiodeanna.openpantrymanager.model.nameKey
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** The stretch of time the costs screen adds up. */
private enum class Period { ThisMonth, LastMonth, ThisYear, All }

/** The first and last day of [period], as of [today]; null for no limit. */
private fun Period.days(today: LocalDate): Pair<LocalDate?, LocalDate?> {
    val month = LocalDate(today.year, today.month, 1)
    return when (this) {
        Period.ThisMonth -> month to null
        Period.LastMonth -> month.minus(DatePeriod(months = 1)) to month.minus(DatePeriod(days = 1))
        Period.ThisYear -> LocalDate(today.year, 1, 1) to null
        Period.All -> null to null
    }
}

/**
 * What the food cost, apart from the pantry's lists: over a period, by product, by group of like
 * products, by the shopping list's sublist, or by shop. A row opens its purchases, which can be put
 * right or deleted; a product can be grouped with like ones, or put in a sublist.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CostsScreen(repository: PantryRepository, onBack: () -> Unit) {
    val costs by repository.costs.collectAsState()
    val memory by repository.memory.collectAsState()
    val shopping by repository.shopping.collectAsState()
    val today = repository.today()
    val texts = strings
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    var period by remember { mutableStateOf(Period.ThisMonth) }
    var grouping by remember { mutableStateOf(CostGrouping.Product) }
    /** The row opened, by its name's key. */
    var open by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Purchase?>(null) }
    var groupFor by remember { mutableStateOf<String?>(null) }
    var sublistFor by remember { mutableStateOf<String?>(null) }

    val (from, to) = period.days(today)
    val lines = remember(costs, memory, grouping, from, to) { costLines(costs, memory, grouping, from, to) }
    val opened = open?.let { key -> lines.firstOrNull { nameKey(it.name) == key } }

    fun announce(message: String, step: UndoStep?) {
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            val result = snackbars.showSnackbar(message, actionLabel = step?.let { texts.undo }, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed && step != null) repository.undo(step)
        }
    }

    fun label(line: CostLine) = when {
        line.name.isNotEmpty() -> line.name
        grouping == CostGrouping.Sublist -> texts.noSublist
        else -> texts.unknownShop
    }

    BackHandler(enabled = opened != null) { open = null }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (opened != null) open = null else onBack() }) {
                        Icon(AppIcons.ArrowBack, contentDescription = strings.back)
                    }
                },
                title = { Text(opened?.let(::label) ?: strings.costs, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Period.entries.forEach { p ->
                        val name = when (p) {
                            Period.ThisMonth -> strings.thisMonth
                            Period.LastMonth -> strings.lastMonth
                            Period.ThisYear -> strings.thisYear
                            Period.All -> strings.allTime
                        }
                        FilterChip(selected = period == p, onClick = { period = p }, label = { Text(name) })
                    }
                }
                if (opened == null) {
                    PrimaryTabRow(selectedTabIndex = grouping.ordinal) {
                        CostGrouping.entries.forEach { g ->
                            val name = when (g) {
                                CostGrouping.Product -> strings.byProduct
                                CostGrouping.Group -> strings.byGroup
                                CostGrouping.Sublist -> strings.bySublist
                                CostGrouping.Shop -> strings.byShop
                            }
                            Tab(selected = grouping == g, onClick = { grouping = g }, text = { Text(name, maxLines = 1) })
                        }
                    }
                    CostList(lines, ::label) { open = nameKey(it.name) }
                } else {
                    CostDetail(
                        line = opened,
                        grouping = grouping,
                        today = today,
                        foodOf = { memory.food(it.food)?.name ?: it.food },
                        groupOf = { memory.groupOf(it) },
                        sublistOf = { memory.food(it)?.sublist.orEmpty() },
                        onPurchase = { editing = it },
                        onGroup = { groupFor = it },
                        onSublist = { sublistFor = it },
                    )
                }
            }
        }
    }

    editing?.let { purchase ->
        PurchaseDialog(
            purchase = purchase,
            today = today,
            onDismiss = { editing = null },
            onDelete = {
                editing = null
                announce(texts.deleted(purchase.food), repository.deletePurchase(purchase.id))
            },
        ) { quantity, cents, day, shop, amount ->
            editing = null
            repository.editPurchase(purchase.id, quantity, cents, day, shop, amount)
        }
    }
    groupFor?.let { food ->
        GroupDialog(
            food = food,
            current = memory.food(food)?.group,
            groups = knownGroups(costs, memory).filter { nameKey(it) != nameKey(memory.groupOf(food)) || memory.food(food)?.group != null },
            onDismiss = { groupFor = null },
        ) { group ->
            groupFor = null
            repository.setGroup(food, group)
        }
    }
    sublistFor?.let { food ->
        val sections = shopping?.doc?.visibleSections().orEmpty().map { it.name }
        val known = memory.foods.values.mapNotNull { it.sublist?.takeIf(String::isNotEmpty) }
        SublistDialog(
            current = memory.food(food)?.sublist.orEmpty(),
            sublists = (sections + known).distinctBy(::nameKey),
            onDismiss = { sublistFor = null },
        ) { sublist ->
            sublistFor = null
            repository.setSublist(food, sublist)
        }
    }
}

/** The total of the period, then each row: what it is, how many and at what price, and what it cost in all. */
@Composable
private fun CostList(lines: List<CostLine>, label: (CostLine) -> String, onOpen: (CostLine) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {
        item(key = "total") {
            Text(
                strings.spent(strings.money(lines.sumOf { it.cents })),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (lines.isEmpty()) {
            item(key = "none") {
                Text(
                    strings.noCosts,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        items(lines, key = { nameKey(it.name) }) { line ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(line) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label(line), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(strings.priceSummary(line), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(strings.money(line.cents), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** "3 pezzi · 1,82 € l'uno · 3,65 €/kg · ultimo -25%". */
private fun Strings.priceSummary(line: CostLine): String = listOfNotNull(
    pieces(line.pieces),
    line.centsPerPiece?.let { perPiece(money(it)) },
    line.centsPerThousand?.let { cents -> line.unit?.let { perThousand(money(cents), it) } },
    line.lastChange?.let { (it * 100).roundToInt() }?.takeIf { it != 0 }?.let(::lastChange),
).joinToString(" · ")

/**
 * One row's purchases, newest first; for a product, its prices shop by shop, its group of like products
 * and its sublist, to change.
 */
@Composable
private fun CostDetail(
    line: CostLine,
    grouping: CostGrouping,
    today: LocalDate,
    foodOf: (Purchase) -> String,
    groupOf: (String) -> String,
    sublistOf: (String) -> String,
    onPurchase: (Purchase) -> Unit,
    onGroup: (String) -> Unit,
    onSublist: (String) -> Unit,
) {
    val food = foodOf(line.purchases.first())
    LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {
        item(key = "summary") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(strings.spent(strings.money(line.cents)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(strings.priceSummary(line), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (grouping == CostGrouping.Product || grouping == CostGrouping.Group) {
            // The same product's price, shop by shop.
            val byShop = line.purchases.groupBy { it.shop.orEmpty() }.takeIf { it.size > 1 }
            byShop?.forEach { (shop, purchases) ->
                item(key = "shop-$shop") {
                    val at = CostLine(shop, purchases)
                    Text(
                        (shop.ifEmpty { strings.unknownShop }) + ": " + strings.priceSummary(at),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }
        }
        if (grouping == CostGrouping.Product) {
            item(key = "actions") {
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onGroup(food) }) {
                        Text(strings.inGroup(groupOf(food)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(onClick = { onSublist(food) }) {
                        Text(sublistOf(food).ifEmpty { null }?.let(strings::inSublist) ?: strings.noSublist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item(key = "heading") {
            Text(
                strings.purchases,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
        items(line.purchases, key = { it.id }) { purchase ->
            Row(
                Modifier.fillMaxWidth().clickable { onPurchase(purchase) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (grouping == CostGrouping.Product) strings.shortDate(purchase.day, today) else foodOf(purchase),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(strings.purchaseSummary(purchase, today, showDay = grouping != CostGrouping.Product), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(strings.money(purchase.cents), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** "12 ott · Coop · 2 pezzi · 1 kg". */
private fun Strings.purchaseSummary(purchase: Purchase, today: LocalDate, showDay: Boolean): String = listOfNotNull(
    purchase.day.takeIf { showDay }?.let { shortDate(it, today) },
    purchase.shop,
    pieces(purchase.quantity),
    purchase.amount?.let(::measure),
).joinToString(" · ")

/** Puts right a purchase: how many, the price, the day, the shop, the weight or volume in all; or deletes it. */
@Composable
private fun PurchaseDialog(
    purchase: Purchase,
    today: LocalDate,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (quantity: Int, cents: Long, day: LocalDate, shop: String?, amount: Measure?) -> Unit,
) {
    var quantity by remember { mutableStateOf(purchase.quantity) }
    var cents by remember { mutableStateOf<Long?>(purchase.cents) }
    var day by remember { mutableStateOf<LocalDate?>(purchase.day) }
    var shop by remember { mutableStateOf(purchase.shop.orEmpty()) }
    var amount by remember { mutableStateOf(purchase.amount) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(purchase.food) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.howMany, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    QuantityStepper(quantity, { quantity = it })
                }
                MoneyField(cents, strings.pricePaid, Modifier.fillMaxWidth()) { cents = it }
                SizeField(amount, strings.amountInAll, Modifier.fillMaxWidth()) { amount = it }
                OutlinedTextField(
                    value = shop,
                    onValueChange = { shop = it },
                    label = { Text(strings.shop) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                BoughtField(day, today) { day = it }
                TextButton(onClick = onDelete) { Text(strings.delete, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(quantity, cents ?: return@TextButton, day ?: purchase.day, shop.trim().takeIf { it.isNotEmpty() }, amount) },
                enabled = cents != null,
            ) { Text(strings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

/**
 * Puts [food]'s costs with like products: in a group already named, a new one, or on its own (then in
 * the group it falls in by itself, by the name it has on the shopping list).
 */
@Composable
private fun GroupDialog(food: String, current: String?, groups: List<String>, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    var choice by remember { mutableStateOf(current) }
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.groupWith) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(food, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChoiceRow(strings.ownGroup, choice == null && !naming) {
                    choice = null
                    naming = false
                }
                groups.filter { nameKey(it) != nameKey(food) }.forEach { group ->
                    ChoiceRow(group, !naming && choice?.let(::nameKey) == nameKey(group)) {
                        choice = group
                        naming = false
                    }
                }
                ChoiceRow(strings.newGroup, naming) { naming = true }
                if (naming) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(strings.groupName) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(if (naming) name.trim() else choice) }, enabled = !naming || name.isNotBlank()) { Text(strings.ok) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

/** Puts a food in a sublist of the shopping list, for its costs and the list alike: one it has, or none. */
@Composable
private fun SublistDialog(current: String, sublists: List<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.sublist) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChoiceRow(strings.noSublist, current.isEmpty()) { onPick("") }
                sublists.forEach { sublist ->
                    ChoiceRow(sublist, nameKey(sublist) == nameKey(current)) { onPick(sublist) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}
