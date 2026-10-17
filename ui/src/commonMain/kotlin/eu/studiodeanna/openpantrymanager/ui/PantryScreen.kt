package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.studiodeanna.openpantrymanager.PantryRepository
import eu.studiodeanna.openpantrymanager.SyncState
import eu.studiodeanna.openpantrymanager.UndoStep
import eu.studiodeanna.openpantrymanager.model.PantryItem
import eu.studiodeanna.openpantrymanager.model.Space
import eu.studiodeanna.openpantrymanager.model.itemsBySpace
import eu.studiodeanna.openpantrymanager.model.liveItems
import eu.studiodeanna.openpantrymanager.model.matchesName
import eu.studiodeanna.openpantrymanager.model.visibleSpaces
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

private sealed interface Dialog {
    data class AddFood(val space: String?) : Dialog
    data object AddSpace : Dialog
    data class RenameSpace(val space: Space) : Dialog
    data class DeleteSpace(val space: Space) : Dialog
    data class Edit(val item: PantryItem) : Dialog
    data class Move(val item: PantryItem) : Dialog
    data class Shopping(val name: String) : Dialog
    data object LinkShopping : Dialog
    data object SharePantry : Dialog
    data object Settings : Dialog
}

/**
 * The pantry: every space as a section that folds, with its food. Ticking food means one of it was
 * used; the last one leaves the pantry, and may go on the shopping list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PantryScreen(
    repository: PantryRepository,
    snackbars: SnackbarHostState,
    onShopping: () -> Unit,
    /** Opens the receipt photos; null where receipts cannot be read. */
    onScan: (() -> Unit)?,
) {
    val doc by repository.pantry.collectAsState()
    val settings by repository.settings.collectAsState()
    val collapsed by repository.collapsedSpaces.collectAsState()
    val shopping by repository.shopping.collectAsState()
    val sharing by repository.sharing.collectAsState()
    val undoState by repository.undoState.collectAsState()
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var menu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf<String?>(null) }
    val today = repository.today()
    val texts = strings

    val spaces = remember(doc) { doc.visibleSpaces() }
    val grouped = remember(doc) { doc.itemsBySpace() }

    /** Says what was done, with Undo; a new message replaces the one showing. */
    fun announce(message: String, step: UndoStep?) {
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            val result = snackbars.showSnackbar(
                message,
                actionLabel = step?.let { texts.undo },
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && step != null) repository.undo(step)
        }
    }

    /** Says what an undo or redo did, replacing the previous message so quick taps don't queue up. */
    fun say(message: String) {
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            snackbars.showSnackbar(message)
        }
    }

    fun undo() = repository.undo()?.let { say(texts.undone(it)) }
    fun redo() = repository.redo()?.let { say(texts.redone(it)) }

    // Holds the keyboard focus whenever no field or dialog has it, so Ctrl+Z, Ctrl+Y and Ctrl+F reach the pantry.
    val keys = remember { FocusRequester() }
    LaunchedEffect(adding, dialog, query) {
        if (adding == null && dialog == null && query == null) runCatching { keys.requestFocus() }
    }

    fun finished(name: String) {
        if (settings.offerShopping) dialog = Dialog.Shopping(name)
    }

    fun useOne(item: PantryItem) {
        val (step, last) = repository.useOne(item.id) ?: return
        if (last) {
            announce(texts.finishedMessage(item.name), step)
            finished(item.name)
        } else {
            announce(texts.left(item.name, item.quantity - 1), step)
        }
    }

    // While searching, Back closes the search rather than the app.
    BackHandler(enabled = query != null) { query = null }

    Scaffold(
        modifier = Modifier
            .onKeyEvent { event ->
                // Text fields take these first, for undoing typing.
                if (event.type != KeyEventType.KeyDown || !(event.isCtrlPressed || event.isMetaPressed)) return@onKeyEvent false
                when {
                    event.key == Key.Z && !event.isShiftPressed -> undo()
                    event.key == Key.Z || event.key == Key.Y -> redo()
                    event.key == Key.F -> query = ""
                    else -> return@onKeyEvent false
                }
                true
            }
            .focusRequester(keys)
            .focusable(),
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = {
                    val q = query
                    if (q == null) {
                        Text(strings.pantry, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        val focus = remember { FocusRequester() }
                        TextField(
                            value = q,
                            onValueChange = { query = it },
                            placeholder = { Text(strings.searchFood) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                        LaunchedEffect(Unit) { focus.requestFocus() }
                    }
                },
                navigationIcon = {
                    if (query != null) {
                        IconButton(onClick = { query = null }) { Icon(AppIcons.ArrowBack, contentDescription = strings.closeSearch) }
                    }
                },
                actions = {
                    if (query == null) {
                        IconButton(onClick = { undo() }, enabled = undoState.canUndo) {
                            Icon(AppIcons.Undo, contentDescription = strings.undo)
                        }
                        IconButton(onClick = { redo() }, enabled = undoState.canRedo) {
                            Icon(AppIcons.Redo, contentDescription = strings.redo)
                        }
                        if (onScan != null && spaces.isNotEmpty()) {
                            IconButton(onClick = onScan) { Icon(AppIcons.Camera, contentDescription = strings.scanReceipt) }
                        }
                        IconButton(onClick = onShopping) { Icon(AppIcons.ShoppingCart, contentDescription = strings.shoppingList) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(AppIcons.MoreVert, contentDescription = strings.more) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                // In the menu, as in Open Check Lists, so that undo and redo fit beside the title.
                                MenuItem(strings.search) { menu = false; query = "" }
                                MenuItem(strings.addSpace) { menu = false; dialog = Dialog.AddSpace }
                                if (spaces.isNotEmpty()) {
                                    MenuItem(strings.expandAll, enabled = collapsed.isNotEmpty()) {
                                        menu = false
                                        repository.setAllCollapsed(emptySet())
                                    }
                                    MenuItem(strings.collapseAll, enabled = spaces.any { it.id !in collapsed }) {
                                        menu = false
                                        adding = null
                                        repository.setAllCollapsed(spaces.mapTo(HashSet()) { it.id })
                                    }
                                }
                                HorizontalDivider()
                                if (sharing == null) MenuItem(strings.sharePantry) { menu = false; dialog = Dialog.SharePantry }
                                MenuItem(strings.settings) { menu = false; dialog = Dialog.Settings }
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (spaces.isNotEmpty() && query == null) {
                ExtendedFloatingActionButton(
                    onClick = { dialog = Dialog.AddFood(null) },
                    icon = { Icon(AppIcons.Add, contentDescription = null) },
                    text = { Text(strings.addFood) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (spaces.isEmpty()) {
                FirstSpaces(
                    onAdd = { repository.addSpace(it) },
                    onOther = { dialog = Dialog.AddSpace },
                    onShare = { dialog = Dialog.SharePantry }.takeIf { sharing == null },
                )
                return@Box
            }
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                (sharing?.sync as? SyncState.Failed)?.let { failed ->
                    item(key = "sharing-problem") {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp)
                                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                strings.sharedPantryProblem(failed.message),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { repository.syncNow() }) {
                                Text(strings.retry, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                val q = query
                if (q != null) {
                    searchResults(spaces, grouped, q, today, onUse = ::useOne, onEdit = { dialog = Dialog.Edit(it) }) {
                        dialog = Dialog.Move(it)
                    }
                    return@LazyColumn
                }
                val soon = doc.liveItems().filter { item -> item.expires?.let { today.daysUntil(it) <= SOON_DAYS } == true }
                    .sortedBy { it.expires }
                if (soon.isNotEmpty()) {
                    item(key = "soon") {
                        UseSoonCard(soon, spaces, today) { dialog = Dialog.Edit(it) }
                    }
                }
                val sections = spaces.map { it.id to it } + (if (grouped[null] != null) listOf(null to null) else emptyList())
                sections.forEachIndexed { index, (id, space) ->
                    val items = grouped[id].orEmpty()
                    val folded = id != null && id in collapsed
                    item(key = "space-$id") {
                        SpaceHeader(
                            name = space?.name ?: strings.withoutSpace,
                            count = items.size,
                            folded = folded,
                            canMoveUp = space != null && index > 0,
                            canMoveDown = space != null && index < spaces.lastIndex,
                            onToggle = {
                                if (id == null) return@SpaceHeader
                                if (!folded && adding == id) adding = null
                                repository.setCollapsed(id, !folded)
                            },
                            onRename = space?.let { { dialog = Dialog.RenameSpace(it) } },
                            onMove = { offset -> id?.let { repository.moveSpace(it, offset) } },
                            onDelete = space?.let {
                                {
                                    if (items.isEmpty()) repository.deleteSpace(it.id) else dialog = Dialog.DeleteSpace(it)
                                }
                            },
                        )
                    }
                    if (!folded) {
                        itemRows(items, today, SECTION_INDENT, onUse = ::useOne, onEdit = { dialog = Dialog.Edit(it) }) {
                            dialog = Dialog.Move(it)
                        }
                        if (id != null) {
                            item(key = "add-$id") {
                                AddItemRow(
                                    open = adding == id,
                                    onOpen = { adding = id },
                                    onClose = { if (adding == id) adding = null },
                                    onAdd = { text -> repository.addItem(text, id) },
                                    modifier = Modifier.padding(start = SECTION_INDENT),
                                )
                            }
                        }
                    }
                }
                item(key = "add-space") {
                    OutlinedButton(
                        onClick = { dialog = Dialog.AddSpace },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        Icon(AppIcons.Add, contentDescription = null)
                        Text(strings.addSpace, Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is Dialog.AddFood -> AddFoodDialog(repository, spaces, d.space, onDismiss = { dialog = null }) { name, space, quantity, expires, bought ->
            dialog = null
            val step = repository.addItem(name, space, quantity, expires, bought)
            announce(texts.addedTo(name, spaces.firstOrNull { it.id == space }?.name.orEmpty()), step)
        }
        Dialog.AddSpace -> AddSpaceDialog(spaces.map { it.name }, onDismiss = { dialog = null }) {
            dialog = null
            repository.addSpace(it)
        }
        is Dialog.RenameSpace -> TextDialog(strings.renameSpace, strings.name, strings.rename, d.space.name, { dialog = null }) {
            dialog = null
            repository.renameSpace(d.space.id, it)
        }
        is Dialog.DeleteSpace -> ConfirmDialog(
            title = strings.deleteTitle(d.space.name),
            message = strings.deleteSpaceMessage,
            confirm = strings.delete,
            onDismiss = { dialog = null },
        ) {
            dialog = null
            repository.deleteSpace(d.space.id)
        }
        is Dialog.Edit -> ItemDialog(
            item = d.item,
            spaces = spaces,
            today = today,
            keepsDays = repository.food(d.item.name)?.keepsDays,
            onDismiss = { dialog = null },
            onSave = { name, quantity, expires, space, bought ->
                dialog = null
                repository.editItem(d.item.id, name, quantity, expires, space, bought)
            },
            onFinished = {
                dialog = null
                announce(texts.finishedMessage(d.item.name), repository.removeItem(d.item.id, finished = true))
                finished(d.item.name)
            },
            onDelete = {
                dialog = null
                announce(texts.deleted(d.item.name), repository.removeItem(d.item.id))
            },
        )
        is Dialog.Move -> MoveDialog(d.item, spaces, repository.moveFor(d.item), onDismiss = { dialog = null }) { space, count ->
            dialog = null
            val step = repository.moveItem(d.item.id, space, count)
            announce(texts.movedTo(d.item.name, spaces.firstOrNull { it.id == space }?.name.orEmpty()), step)
        }
        is Dialog.Shopping -> ShoppingPromptDialog(
            name = d.name,
            shopping = shopping,
            suggested = repository.sublistFor(d.name),
            similar = repository::similarOnList,
            initialText = repository.shoppingNameFor(d.name),
            onDismiss = { dialog = null },
            onLink = { dialog = Dialog.LinkShopping },
            onAdd = { text, sublist ->
                dialog = null
                repository.addToShopping(d.name, text, sublist)
                announce(texts.onShoppingList(text), null)
            },
            onUse = { item ->
                dialog = null
                repository.useShoppingItem(d.name, item.id)
                announce(if (item.done) texts.onShoppingList(item.text) else texts.alreadyOnShoppingList(item.text), null)
            },
        )
        Dialog.LinkShopping -> LinkShoppingFlow(
            link = repository::linkShoppingList,
            onDismiss = { dialog = null },
            onDone = { dialog = null },
        )
        Dialog.SharePantry -> SharePantryFlow(
            share = { link, join -> repository.sharePantry(link, texts.pantry, join) },
            onDismiss = { dialog = null },
            onDone = { dialog = null },
        )
        Dialog.Settings -> SettingsDialog(
            settings = settings,
            shopping = shopping,
            sharing = sharing,
            onChange = { scope.launch { repository.updateSettings(it) } },
            onLinkShopping = { dialog = Dialog.LinkShopping },
            onUnlinkShopping = { scope.launch { repository.unlinkShoppingList() } },
            onSharePantry = { dialog = Dialog.SharePantry },
            onStopSharing = { scope.launch { repository.stopSharingPantry() } },
            onSyncNow = { repository.syncNow() },
            onDismiss = { dialog = null },
        )
    }
}

/** How far a space's food sits to the right of its header, so it reads as belonging to it. */
private val SECTION_INDENT = 24.dp

@Composable
private fun MenuItem(text: String, enabled: Boolean = true, onClick: () -> Unit) =
    DropdownMenuItem(text = { Text(text) }, onClick = onClick, enabled = enabled)

/** With no spaces yet: suggests the usual ones, one tap each, or any other name. */
@Composable
private fun FirstSpaces(onAdd: (String) -> Unit, onOther: () -> Unit, onShare: (() -> Unit)?) {
    Column(
        Modifier.widthIn(max = 480.dp).fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(strings.whereFoodIs, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            strings.whereFoodIsHelp,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            strings.commonSpaces.forEach { name ->
                AssistChip(onClick = { onAdd(name) }, label = { Text(name) }, leadingIcon = { Icon(AppIcons.Add, null) })
            }
        }
        TextButton(onClick = onOther) { Text(strings.otherSpace) }
        // A second phone joins the pantry another one shares.
        onShare?.let { TextButton(onClick = it) { Text(strings.useSharedPantry) } }
    }
}

/** Food that expires within [SOON_DAYS] days or already has, soonest first. */
@Composable
private fun UseSoonCard(items: List<PantryItem>, spaces: List<Space>, today: LocalDate, onEdit: (PantryItem) -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                Text(
                    strings.useSoon,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().clickable { onEdit(item) }.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(
                            spaces.firstOrNull { it.id == item.space }?.name.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                        )
                    }
                    val expires = item.expires ?: return@Row
                    Text(strings.expiry(expires, today), style = MaterialTheme.typography.labelLarge, color = expiryColor(expires, today))
                }
            }
        }
    }
}

private fun LazyListScope.itemRows(
    items: List<PantryItem>,
    today: LocalDate,
    indent: Dp,
    onUse: (PantryItem) -> Unit,
    onEdit: (PantryItem) -> Unit,
    onMove: (PantryItem) -> Unit,
) {
    items(items, key = { it.id }) { item ->
        ItemRow(item, today, indent, onUse = { onUse(item) }, onEdit = { onEdit(item) }, onMove = { onMove(item) })
    }
}

/** While searching: the matching food of every space, folded or not, under its space's name. */
private fun LazyListScope.searchResults(
    spaces: List<Space>,
    grouped: Map<String?, List<PantryItem>>,
    query: String,
    today: LocalDate,
    onUse: (PantryItem) -> Unit,
    onEdit: (PantryItem) -> Unit,
    onMove: (PantryItem) -> Unit,
) {
    var any = false
    for (space in spaces) {
        val found = grouped[space.id].orEmpty().filter { matchesName(it.name, query) }
        if (found.isEmpty()) continue
        any = true
        item(key = "found-${space.id}") {
            Text(
                space.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
        itemRows(found, today, 0.dp, onUse, onEdit, onMove)
    }
    if (!any && query.isNotBlank()) {
        item(key = "none") {
            Text(
                strings.noFoodMatches(query),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

@Composable
private fun ItemRow(item: PantryItem, today: LocalDate, indent: Dp, onUse: () -> Unit, onEdit: () -> Unit, onMove: () -> Unit) {
    val usedOne = strings.usedOne(item.name)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(start = 4.dp + indent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Ticking takes one: the box never stays ticked, the food leaves or its count goes down.
        Checkbox(checked = false, onCheckedChange = { onUse() }, modifier = Modifier.semantics { contentDescription = usedOne })
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.expires?.let {
                Text(strings.expiry(it, today), style = MaterialTheme.typography.labelMedium, color = expiryColor(it, today))
            }
        }
        if (item.quantity > 1) {
            Text(
                "×${item.quantity}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        IconButton(onClick = onMove) { Icon(AppIcons.Move, contentDescription = strings.moveTitle(item.name)) }
    }
}

@Composable
private fun SpaceHeader(
    name: String,
    count: Int,
    folded: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onRename: (() -> Unit)?,
    onMove: (Int) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 16.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClickLabel = if (folded) strings.expandSpace else strings.collapseSpace, onClick = onToggle)
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            if (count > 0) {
                Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                if (folded) AppIcons.KeyboardArrowDown else AppIcons.KeyboardArrowUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
            if (onRename != null && onDelete != null) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(AppIcons.MoreVert, contentDescription = strings.spaceOptions) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        MenuItem(strings.rename) { menu = false; onRename() }
                        if (canMoveUp) MenuItem(strings.moveUp) { menu = false; onMove(-1) }
                        if (canMoveDown) MenuItem(strings.moveDown) { menu = false; onMove(1) }
                        MenuItem(strings.deleteSpace) { menu = false; onDelete() }
                    }
                }
            } else {
                Box(Modifier.size(48.dp))
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
}

/** A collapsed "Add food" button that opens into a field; Enter adds and keeps it open for the next one. */
@Composable
private fun AddItemRow(
    open: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!open) {
        TextButton(onClick = onOpen, modifier = modifier.padding(start = 8.dp)) {
            Icon(AppIcons.Add, contentDescription = null)
            Text(strings.addHere, Modifier.padding(start = 8.dp))
        }
        return
    }
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    fun submit() {
        if (text.isNotBlank()) onAdd(text.trim())
        text = ""
    }
    TextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(strings.addHere) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (text.isBlank()) onClose() else submit() }),
        trailingIcon = {
            IconButton(onClick = onClose) { Icon(AppIcons.Close, contentDescription = strings.doneAdding) }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp).focusRequester(focus),
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}
