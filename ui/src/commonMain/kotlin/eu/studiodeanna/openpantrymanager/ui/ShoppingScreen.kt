package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.studiodeanna.openpantrymanager.PantryRepository
import eu.studiodeanna.openpantrymanager.SyncState
import eu.studiodeanna.openpantrymanager.checklist.Item
import eu.studiodeanna.openpantrymanager.checklist.itemsBySection
import eu.studiodeanna.openpantrymanager.checklist.visibleSections
import eu.studiodeanna.openpantrymanager.currentTimeMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val POLL_MILLIS = 15_000L

private enum class ShoppingDialog { Link, Add, Unlink }

/**
 * The linked Open Check Lists list, to look at and tick while shopping, and to add to. Without a
 * linked list, it explains how to link one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(repository: PantryRepository, onBack: () -> Unit) {
    val shopping by repository.shopping.collectAsState()
    val list = shopping
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<ShoppingDialog?>(null) }
    var menu by remember { mutableStateOf(false) }
    val undoState by repository.shoppingUndoState.collectAsState()
    val snackbars = remember { SnackbarHostState() }
    val texts = strings

    /** Says what an undo or redo did, replacing the previous message so quick taps don't queue up. */
    fun say(message: String) {
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            snackbars.showSnackbar(message)
        }
    }

    fun undo() = repository.undoShopping()?.let { say(texts.undone(it)) }
    fun redo() = repository.redoShopping()?.let { say(texts.redone(it)) }

    // Holds the keyboard focus whenever no dialog has it, so Ctrl+Z and Ctrl+Y reach the list.
    val keys = remember { FocusRequester() }
    LaunchedEffect(dialog) {
        if (dialog == null) runCatching { keys.requestFocus() }
    }

    val linked = list != null
    LaunchedEffect(linked) {
        while (linked) {
            repository.syncNow()
            delay(POLL_MILLIS)
        }
    }

    Scaffold(
        modifier = Modifier
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !(event.isCtrlPressed || event.isMetaPressed)) return@onKeyEvent false
                when {
                    event.key == Key.Z && !event.isShiftPressed -> undo()
                    event.key == Key.Z || event.key == Key.Y -> redo()
                    else -> return@onKeyEvent false
                }
                true
            }
            .focusRequester(keys)
            .focusable(),
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = strings.back) }
                },
                title = {
                    Column {
                        Text(list?.doc?.title ?: strings.shoppingList, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (list != null) {
                            Text(
                                describe(list.sync, strings),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (list.sync is SyncState.Failed) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (list != null) {
                        IconButton(onClick = { undo() }, enabled = undoState.canUndo) {
                            Icon(AppIcons.Undo, contentDescription = strings.undo)
                        }
                        IconButton(onClick = { redo() }, enabled = undoState.canRedo) {
                            Icon(AppIcons.Redo, contentDescription = strings.redo)
                        }
                        IconButton(onClick = { repository.syncNow() }) { Icon(AppIcons.Sync, contentDescription = strings.syncNow) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(AppIcons.MoreVert, contentDescription = strings.more) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(strings.linkAnother) }, onClick = { menu = false; dialog = ShoppingDialog.Link })
                                DropdownMenuItem(text = { Text(strings.unlink) }, onClick = { menu = false; dialog = ShoppingDialog.Unlink })
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (list != null) {
                ExtendedFloatingActionButton(
                    onClick = { dialog = ShoppingDialog.Add },
                    icon = { Icon(AppIcons.Add, contentDescription = null) },
                    text = { Text(strings.add) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (list == null) {
                Column(
                    Modifier.widthIn(max = 480.dp).fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(strings.noShoppingListTitle, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text(
                        strings.noShoppingListHelp,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = { dialog = ShoppingDialog.Link }) { Text(strings.linkShoppingList) }
                }
                return@Box
            }
            val doc = list.doc
            val grouped = remember(doc) { doc.itemsBySection() }
            LazyColumn(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                (list.sync as? SyncState.Failed)?.let { failed ->
                    item(key = "sync-error") {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp)
                                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                failed.message,
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
                shoppingRows(grouped[null].orEmpty(), indent = false) { item, done -> repository.setShoppingDone(item.id, done) }
                for (section in doc.visibleSections()) {
                    val items = grouped[section.id].orEmpty()
                    val folded = section.id in list.collapsed
                    item(key = "section-${section.id}") {
                        Column(Modifier.padding(top = 16.dp)) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { repository.setShoppingCollapsed(section.id, !folded) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    section.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (items.isNotEmpty()) {
                                    Text(
                                        "${items.count { it.done }}/${items.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    if (folded) AppIcons.KeyboardArrowDown else AppIcons.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        }
                    }
                    if (!folded) {
                        shoppingRows(items, indent = true) { item, done -> repository.setShoppingDone(item.id, done) }
                    }
                }
            }
        }
    }

    when (dialog) {
        null -> Unit
        ShoppingDialog.Link -> LinkShoppingFlow(
            link = repository::linkShoppingList,
            onDismiss = { dialog = null },
            onDone = { dialog = null },
        )
        ShoppingDialog.Add -> ShoppingPromptDialog(
            name = "",
            shopping = list,
            suggested = null,
            similar = repository::similarOnList,
            askName = true,
            onDismiss = { dialog = null },
            onLink = { dialog = ShoppingDialog.Link },
            onAdd = { name, sublist ->
                dialog = null
                repository.addToShopping(name, sublist)
            },
            onUse = { item ->
                dialog = null
                repository.useShoppingItem(item.text, item.id)
            },
        )
        ShoppingDialog.Unlink -> ConfirmDialog(
            title = strings.unlinkTitle,
            message = strings.unlinkMessage,
            confirm = strings.unlink,
            onDismiss = { dialog = null },
        ) {
            dialog = null
            scope.launch { repository.unlinkShoppingList() }
        }
    }
}

/** Unticked items first, so what is left to buy stays together at the top. */
private fun LazyListScope.shoppingRows(items: List<Item>, indent: Boolean, onToggle: (Item, Boolean) -> Unit) {
    items(items.sortedBy { it.done }, key = { it.id }) { item ->
        Row(
            Modifier.fillMaxWidth().clickable { onToggle(item, !item.done) }.padding(start = if (indent) 28.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = item.done, onCheckedChange = { onToggle(item, it) })
            Text(
                item.text,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
        }
    }
}

fun describe(sync: SyncState, strings: Strings): String = when (sync) {
    SyncState.Syncing -> strings.syncing
    is SyncState.Failed -> sync.message
    is SyncState.Synced -> {
        val minutes = (currentTimeMillis() - sync.at) / 60_000
        when {
            minutes < 1 -> strings.syncedJustNow
            minutes < 60 -> strings.syncedMinutesAgo(minutes)
            minutes < 48 * 60 -> strings.syncedHoursAgo(minutes / 60)
            else -> strings.syncedDaysAgo(minutes / (24 * 60))
        }
    }
}
