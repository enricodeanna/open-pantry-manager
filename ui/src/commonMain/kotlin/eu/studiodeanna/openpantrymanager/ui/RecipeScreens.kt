package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.studiodeanna.openpantrymanager.Cooked
import eu.studiodeanna.openpantrymanager.Dish
import eu.studiodeanna.openpantrymanager.PantryRepository
import eu.studiodeanna.openpantrymanager.RecipeUse
import eu.studiodeanna.openpantrymanager.Sublist
import eu.studiodeanna.openpantrymanager.costs.Measure
import eu.studiodeanna.openpantrymanager.memory.Ingredient
import eu.studiodeanna.openpantrymanager.memory.Recipe
import eu.studiodeanna.openpantrymanager.memory.food
import eu.studiodeanna.openpantrymanager.memory.isBoxed
import eu.studiodeanna.openpantrymanager.model.PantryItem
import eu.studiodeanna.openpantrymanager.model.Space
import eu.studiodeanna.openpantrymanager.model.item
import eu.studiodeanna.openpantrymanager.model.itemsBySpace
import eu.studiodeanna.openpantrymanager.model.matchesName
import eu.studiodeanna.openpantrymanager.model.nameKey
import eu.studiodeanna.openpantrymanager.model.visibleSpaces
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** Asks which recipe was made: a name typed, or one made before, offered as it is typed (the latest first while nothing is). */
@Composable
fun RecipeNameDialog(known: List<Recipe>, onDismiss: () -> Unit, onNext: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val offered = remember(name, known) {
        val key = nameKey(name)
        known.filter { key.isEmpty() || nameKey(it.name).let { k -> k.startsWith(key) || k.split(' ').any { w -> w.startsWith(key) } } }
            .filterNot { nameKey(it.name) == key }
            .take(5)
    }
    fun next() {
        if (name.isNotBlank()) onNext(name.trim())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.whatDidYouMake) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.recipeName) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { next() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                offered.forEach { recipe ->
                    Text(
                        recipe.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { onNext(recipe.name) }.padding(vertical = 8.dp),
                    )
                }
            }
            LaunchedEffect(Unit) { focus.requestFocus() }
        },
        confirmButton = { TextButton(onClick = ::next, enabled = name.isNotBlank()) { Text(strings.next) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

/**
 * Making the recipe [name]: the pantry, to tick what went into it, how many of each, and of boxes
 * whether they were used up or some is left (and how much was used, of a box of known size); and what of
 * the dish goes in the pantry, leftovers or all of it made ahead, where, how many portions. A recipe made
 * before comes with what it took last time ticked, as far as the pantry has it, says what is missing, and
 * offers to put away what was put away last time. Saving does it all in one edit, learns the recipe, and
 * offers what ran out for the shopping list; [onDone] then.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CookScreen(repository: PantryRepository, name: String, onClose: () -> Unit, onDone: (Cooked) -> Unit) {
    val doc by repository.pantry.collectAsState()
    val settings by repository.settings.collectAsState()
    val shopping by repository.shopping.collectAsState()
    val today = repository.today()
    val suggestion = remember { repository.suggestFor(name) }
    val known = remember { repository.recipe(name) }
    /** What of the dish goes in the pantry: null for nothing, else leftovers or the whole of it; as last time, to start with. */
    var keptLeftover by remember { mutableStateOf(known?.kept?.leftover) }
    var portions by remember { mutableStateOf(known?.kept?.portions ?: 1) }
    val usual = remember { repository.placeFor(name) }
    var space by remember { mutableStateOf(usual) }
    var expires by remember { mutableStateOf<LocalDate?>(null) }
    val picked = remember { mutableStateMapOf<String, RecipeUse>().apply { suggestion?.uses?.forEach { put(it.item, it) } } }
    /** The items ticked, in the order they were: the recipe's ingredients go in that order. */
    val order = remember { mutableStateListOf<String>().apply { suggestion?.uses?.forEach { add(it.item) } } }
    var cooked by remember { mutableStateOf<Cooked?>(null) }
    val spaces = remember(doc) { doc.visibleSpaces() }
    // Looked for by name here: each space in alphabetical order, the same food opened first, then soonest to expire.
    val grouped = remember(doc) {
        doc.itemsBySpace().mapValues { (_, items) ->
            items.sortedWith(compareBy({ nameKey(it.name) }, { it.opened == null }, { it.expires == null }, { it.expires }))
        }
    }
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { Icon(AppIcons.Close, contentDescription = strings.cancel) } },
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    val dish = keptLeftover?.let { leftover ->
                        space?.takeIf { id -> spaces.any { it.id == id } }?.let { Dish(it, portions, expires, leftover) }
                    }
                    TextButton(
                        onClick = {
                            val done = repository.cook(name, order.mapNotNull { picked[it] }, dish)
                            // What ran out goes on the shopping list after a look; without a list, there is none to put it on.
                            if (done.ranOut.isNotEmpty() && settings.offerShopping && shopping != null) cooked = done else onDone(done)
                        },
                        // Something taken, or something put away; and a place for what is put away.
                        enabled = (picked.isNotEmpty() || dish != null) && (keptLeftover == null || dish != null),
                    ) { Text(strings.save, fontWeight = FontWeight.SemiBold) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                // Above the list, so it stays at hand however far the list scrolls.
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(strings.searchFood) },
                    leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(AppIcons.Close, contentDescription = strings.clearSearch) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 48.dp)) {
                    if (picked.isNotEmpty()) {
                        item(key = "ticked") {
                            val texts = strings
                            Text(
                                texts.ticked(order.mapNotNull { id -> picked[id]?.let { use -> doc.item(id)?.let { texts.useText(it, use) } } }.joinToString(", ")),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                            )
                        }
                    }
                    if (query.isBlank()) item(key = "intro") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(strings.tickIngredients, style = MaterialTheme.typography.bodyMedium)
                            if (suggestion != null && suggestion.uses.isNotEmpty()) {
                                Text(strings.asLastTime(suggestion.uses.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            if (suggestion != null && suggestion.missing.isNotEmpty()) {
                                Text(
                                    strings.notInPantry(suggestion.missing.joinToString(", ") { it.food }),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                    if (query.isBlank()) item(key = "kept") {
                        KeptCard(
                            leftover = keptLeftover,
                            onKind = { keptLeftover = it },
                            spaces = spaces,
                            space = space,
                            usual = usual,
                            onSpace = { space = it },
                            portions = portions,
                            onPortions = { portions = it },
                            expires = expires,
                            today = today,
                            keepsDays = repository.food(name)?.keepsDays,
                            onExpires = { expires = it },
                        )
                    }
                    val found = spaces.associateWith { space -> grouped[space.id].orEmpty().filter { query.isBlank() || matchesName(it.name, query) } }
                    if (found.values.all { it.isEmpty() } && query.isNotBlank()) {
                        item(key = "none") {
                            Text(
                                strings.noFoodMatches(query),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                    for (space in spaces) {
                        val items = found[space].orEmpty()
                        if (items.isEmpty()) continue
                        item(key = "space-${space.id}") {
                            Text(
                                space.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        }
                        items(items, key = { it.id }) { item ->
                            IngredientRow(item, today, picked[item.id]) { use ->
                                if (use == null) {
                                    picked.remove(item.id)
                                    order.remove(item.id)
                                } else {
                                    if (item.id !in picked) order += item.id
                                    picked[item.id] = use
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    cooked?.let { done ->
        RanOutDialog(done.ranOut, onDismiss = { onDone(done) }) { foods ->
            foods.forEach { food -> repository.addToShopping(food, repository.shoppingNameFor(food), repository.sublistFor(food) ?: Sublist.None) }
            onDone(done)
        }
    }
}

/**
 * What of the dish goes in the pantry: nothing, leftovers, or all of it, made ahead ([leftover]: null,
 * true, false); then where, how many portions, until when.
 */
@Composable
private fun KeptCard(
    leftover: Boolean?,
    onKind: (Boolean?) -> Unit,
    spaces: List<Space>,
    space: String?,
    usual: String?,
    onSpace: (String) -> Unit,
    portions: Int,
    onPortions: (Int) -> Unit,
    expires: LocalDate?,
    today: LocalDate,
    keepsDays: Int?,
    onExpires: (LocalDate?) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.whatIsKept, style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = leftover == null, onClick = { onKind(null) }, label = { Text(strings.nothing) })
                FilterChip(selected = leftover == true, onClick = { onKind(true) }, label = { Text(strings.leftover) })
                FilterChip(selected = leftover == false, onClick = { onKind(false) }, label = { Text(strings.wholeDish) })
            }
            if (leftover == null) return@Column
            Text(strings.where, style = MaterialTheme.typography.labelLarge)
            SpaceChips(spaces, space, usual, onSelect = onSpace)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.portions, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                QuantityStepper(portions, onPortions)
            }
            ExpiryField(expires, today, keepsDays, onChange = onExpires)
        }
    }
}

/**
 * An item of the pantry, to tick as gone into the recipe; ticked, how many, and for a box whether it was
 * used up or some is left, and then how much was used.
 */
@Composable
private fun IngredientRow(item: PantryItem, today: LocalDate, use: RecipeUse?, onChange: (RecipeUse?) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp)) {
        Row(
            // A box ticked is opened, some left, until said used up: nothing leaves the pantry by mistake.
            Modifier.fillMaxWidth().clickable { onChange(if (use == null) RecipeUse(item.id, 1, whole = !item.boxed) else null) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = use != null, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                Text(strings.ingredientInfo(item, today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (use == null) return@Column
        Column(Modifier.padding(start = 48.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (item.boxed) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = use.whole, onClick = { onChange(use.copy(whole = true, amount = null)) }, label = { Text(strings.usedUp) })
                    FilterChip(selected = !use.whole, onClick = { onChange(use.copy(whole = false, quantity = 1)) }, label = { Text(strings.someLeft) })
                }
            }
            if (use.whole && item.quantity > 1) {
                QuantityStepper(use.quantity, { onChange(use.copy(quantity = it)) }, max = item.quantity)
            }
            val size = item.size
            if (!use.whole && size != null) {
                AmountField(use.amount, size.unit, strings.amountUsed, Modifier.fillMaxWidth()) { amount ->
                    onChange(use.copy(amount = amount?.takeIf { it > 0 }))
                }
            }
        }
    }
}

/** What of an item goes into the recipe: "Uova ×2", "Farina (aperta, 250 g)", "Latte". */
private fun Strings.useText(item: PantryItem, use: RecipeUse): String = buildString {
    append(item.name)
    if (use.quantity > 1) append(" ×").append(use.quantity)
    if (item.boxed && !use.whole) {
        append(" (").append(ingredientOpened(use.amount?.let { a -> item.size?.let { measure(Measure(a, it.unit)) } })).append(")")
    }
}

/** How many there are of an item, and whether opened and what is left: "6", "2 · Aperto oggi · restano 350 g". */
private fun Strings.ingredientInfo(item: PantryItem, today: LocalDate): String = listOfNotNull(
    item.quantity.toString(),
    item.leftover.takeIf { it }?.let { leftover },
    item.opened?.let { openedAgo(it.daysUntil(today)) },
    item.size?.let { size -> item.left?.let { amountLeft(measure(Measure(it, size.unit))) } },
    item.expires?.let { expiry(it, today) },
).joinToString(" · ")

/** The foods a recipe left none of unopened, to tick for the shopping list. */
@Composable
private fun RanOutDialog(foods: List<String>, onDismiss: () -> Unit, onAdd: (List<String>) -> Unit) {
    val ticked = remember { mutableStateListOf(*foods.toTypedArray()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.ranOutTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(strings.putOnListQuestion, style = MaterialTheme.typography.bodyMedium)
                foods.forEach { food ->
                    CheckRow(food, food in ticked) { on -> if (on) ticked += food else ticked -= food }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(ticked.toList()) }, enabled = ticked.isNotEmpty()) { Text(strings.add) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.notNow) } },
    )
}

/**
 * The recipes made or written, to look at, change (name, ingredients, how much of each), delete, write
 * anew, or make ([onCook]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun RecipesScreen(repository: PantryRepository, onBack: () -> Unit, onCook: (String) -> Unit) {
    val memory by repository.memory.collectAsState()
    val today = repository.today()
    val recipes = remember(memory) { memory.recipes.values.sortedBy { nameKey(it.name) } }
    /** The recipe being changed: its name's key, "" for a new one. */
    var open by remember { mutableStateOf<String?>(null) }
    val editing = open?.let { key -> if (key.isEmpty()) Recipe("") else recipes.firstOrNull { nameKey(it.name) == key } }

    BackHandler(enabled = editing != null) { open = null }

    if (editing != null) {
        RecipeEditor(
            recipe = editing,
            repository = repository,
            onClose = { open = null },
            onSave = { recipe ->
                repository.saveRecipe(recipe, was = editing.name.ifEmpty { recipe.name })
                open = null
            },
            onDelete = {
                repository.deleteRecipe(editing.name)
                open = null
            },
            onCook = { recipe ->
                repository.saveRecipe(recipe, was = editing.name.ifEmpty { recipe.name })
                onCook(recipe.name)
            },
        )
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = strings.back) } },
                title = { Text(strings.recipes) },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { open = "" },
                icon = { Icon(AppIcons.Add, contentDescription = null) },
                text = { Text(strings.addRecipe) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxWidth(), contentPadding = PaddingValues(bottom = 96.dp)) {
                if (recipes.isEmpty()) {
                    item(key = "none") {
                        Text(
                            strings.noRecipes,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                items(recipes, key = { nameKey(it.name) }) { recipe ->
                    val texts = strings
                    Column(Modifier.fillMaxWidth().clickable { open = nameKey(recipe.name) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(recipe.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            recipe.ingredients.joinToString(" · ") { texts.ingredientText(it, memory.food(it.food)?.size) },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (recipe.made > 0) {
                            Text(
                                strings.madeTimes(recipe.made, recipe.last?.let { strings.shortDate(it, today) }),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "Uova ×2", "Farina (aperta, 250 g)", "Latte". */
private fun Strings.ingredientText(ingredient: Ingredient, size: Measure?): String = buildString {
    append(ingredient.food)
    if (ingredient.quantity > 1) append(" ×").append(ingredient.quantity)
    if (!ingredient.whole) append(" (").append(ingredientOpened(ingredient.amount?.let { a -> size?.let { measure(Measure(a, it.unit)) } })).append(")")
}

/** Changes a recipe: its name, and its ingredients, how many of each, and of boxes whether used up or opened, and how much. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeEditor(
    recipe: Recipe,
    repository: PantryRepository,
    onClose: () -> Unit,
    onSave: (Recipe) -> Unit,
    onDelete: () -> Unit,
    onCook: (Recipe) -> Unit,
) {
    var name by remember { mutableStateOf(recipe.name) }
    val ingredients = remember { mutableStateListOf(*recipe.ingredients.toTypedArray()) }
    var adding by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    val memory by repository.memory.collectAsState()
    fun written() = recipe.copy(name = name.trim(), ingredients = ingredients.toList())
    val ok = name.isNotBlank()
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { Icon(AppIcons.ArrowBack, contentDescription = strings.back) } },
                title = { Text(recipe.name.ifEmpty { strings.addRecipe }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = { TextButton(onClick = { onSave(written()) }, enabled = ok) { Text(strings.save, fontWeight = FontWeight.SemiBold) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp)) {
                item(key = "name") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(strings.recipeName) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                    Text(strings.ingredients, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                }
                items(ingredients.indices.toList(), key = { "${it}-${ingredients.getOrNull(it)?.food}" }) { index ->
                    val ingredient = ingredients.getOrNull(index) ?: return@items
                    val boxed = memory.isBoxed(ingredient.food) || !ingredient.whole
                    val size = memory.food(ingredient.food)?.size
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ingredient.food, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { ingredients.removeAt(index) }) {
                                Icon(AppIcons.Close, contentDescription = strings.removeIngredient(ingredient.food))
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                            if (ingredient.whole) QuantityStepper(ingredient.quantity, { ingredients[index] = ingredient.copy(quantity = it) })
                            if (boxed) {
                                FilterChip(selected = ingredient.whole, onClick = { ingredients[index] = ingredient.copy(whole = true, amount = null) }, label = { Text(strings.usedUp) })
                                FilterChip(selected = !ingredient.whole, onClick = { ingredients[index] = ingredient.copy(whole = false, quantity = 1) }, label = { Text(strings.someLeft) })
                            }
                        }
                        if (!ingredient.whole && size != null) {
                            AmountField(ingredient.amount, size.unit, strings.amountUsed, Modifier.fillMaxWidth()) { amount ->
                                ingredients[index] = ingredient.copy(amount = amount?.takeIf { it > 0 })
                            }
                        }
                        HorizontalDivider(Modifier.padding(top = 4.dp))
                    }
                }
                item(key = "add") {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        OutlinedTextField(
                            value = adding,
                            onValueChange = { adding = it },
                            label = { Text(strings.addIngredient) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (adding.isNotBlank()) ingredients += Ingredient(repository.food(adding)?.name ?: adding.trim())
                                adding = ""
                            }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val offered = remember(adding) {
                            repository.complete(adding).filter { food -> ingredients.none { nameKey(it.food) == nameKey(food.name) } }.take(4)
                        }
                        offered.forEach { food ->
                            Text(
                                food.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    ingredients += Ingredient(food.name)
                                    adding = ""
                                }.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
                item(key = "actions") {
                    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onCook(written()) }, enabled = ok) {
                            Icon(AppIcons.Recipe, contentDescription = null)
                            Text(strings.makeIt, Modifier.padding(start = 8.dp))
                        }
                        if (recipe.name.isNotEmpty()) {
                            TextButton(onClick = { deleting = true }) { Text(strings.delete, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    if (deleting) {
        ConfirmDialog(strings.deleteTitle(recipe.name), "", strings.delete, onDismiss = { deleting = false }) {
            deleting = false
            onDelete()
        }
    }
}
