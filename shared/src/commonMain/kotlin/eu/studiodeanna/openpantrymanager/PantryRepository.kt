package eu.studiodeanna.openpantrymanager

import eu.studiodeanna.openpantrymanager.checklist.ListCodec
import eu.studiodeanna.openpantrymanager.checklist.ListDocument
import eu.studiodeanna.openpantrymanager.checklist.ListEditor
import eu.studiodeanna.openpantrymanager.checklist.merge
import eu.studiodeanna.openpantrymanager.checklist.pruned
import eu.studiodeanna.openpantrymanager.checklist.visibleSections
import eu.studiodeanna.openpantrymanager.memory.Food
import eu.studiodeanna.openpantrymanager.memory.Memory
import eu.studiodeanna.openpantrymanager.memory.complete
import eu.studiodeanna.openpantrymanager.memory.food
import eu.studiodeanna.openpantrymanager.memory.moveFor
import eu.studiodeanna.openpantrymanager.memory.moved
import eu.studiodeanna.openpantrymanager.memory.placeFor
import eu.studiodeanna.openpantrymanager.memory.placed
import eu.studiodeanna.openpantrymanager.memory.receiptKey
import eu.studiodeanna.openpantrymanager.memory.receiptMeans
import eu.studiodeanna.openpantrymanager.memory.receiptSkips
import eu.studiodeanna.openpantrymanager.memory.wentOnSublist
import eu.studiodeanna.openpantrymanager.model.PantryCodec
import eu.studiodeanna.openpantrymanager.model.PantryDocument
import eu.studiodeanna.openpantrymanager.model.PantryEditor
import eu.studiodeanna.openpantrymanager.model.PantryItem
import eu.studiodeanna.openpantrymanager.model.changedItems
import eu.studiodeanna.openpantrymanager.model.item
import eu.studiodeanna.openpantrymanager.model.nameKey
import eu.studiodeanna.openpantrymanager.model.pruned
import eu.studiodeanna.openpantrymanager.model.visibleSpaces
import eu.studiodeanna.openpantrymanager.receipt.OcrLine
import eu.studiodeanna.openpantrymanager.receipt.ReceiptEntry
import eu.studiodeanna.openpantrymanager.receipt.ReceiptParser
import eu.studiodeanna.openpantrymanager.receipt.Recognizer
import eu.studiodeanna.openpantrymanager.store.DeviceState
import eu.studiodeanna.openpantrymanager.store.FileStore
import eu.studiodeanna.openpantrymanager.store.Settings
import eu.studiodeanna.openpantrymanager.sync.RemoteException
import eu.studiodeanna.openpantrymanager.sync.ShareLink
import eu.studiodeanna.openpantrymanager.sync.ShareLinks
import eu.studiodeanna.openpantrymanager.sync.SharedList
import eu.studiodeanna.openpantrymanager.sync.nextcloudShareFor
import eu.studiodeanna.openpantrymanager.sync.remoteFileFor
import eu.studiodeanna.openpantrymanager.sync.syncWith
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.time.Clock

sealed interface SyncState {
    data object Syncing : SyncState
    data class Synced(val at: Long) : SyncState
    data class Failed(val message: String) : SyncState
}

/** The Open Check Lists list this device puts finished food on, as last synced, and how syncing goes. */
data class ShoppingList(
    val link: ShareLink,
    val doc: ListDocument,
    val sync: SyncState,
    /** Sublists folded away on this device. */
    val collapsed: Set<String> = emptySet(),
)

/** Where on the shopping list something goes: outside sublists, in one, or in a new one. */
sealed interface Sublist {
    data object None : Sublist
    data class Existing(val id: String) : Sublist
    data class New(val name: String) : Sublist
}

/** Thrown by [PantryRepository.linkShoppingList] when a shared folder holds several lists to choose from. */
class ListChoiceNeeded(val link: ShareLink, val lists: List<SharedList>) : RemoteException(Messages.current.chooseList)

/**
 * One change to the pantry that the user can take back: the items it touched as they were before,
 * and what the app knew before learning from it.
 */
class UndoStep internal constructor(
    internal val items: Map<String, PantryItem?>,
    internal val memory: Memory,
)

/**
 * The pantry on this device, what the app has learned, and the linked shopping list. Edits are saved
 * at once; the shopping list syncs shortly after each change and whenever [syncShopping] is called.
 */
class PantryRepository(
    private val files: FileStore,
    private val scope: CoroutineScope,
    private val http: HttpClient = defaultHttpClient(),
    private val now: () -> Long = ::currentTimeMillis,
    private val date: () -> LocalDate = ::systemToday,
    private val syncDelayMillis: Long = 800,
) {
    private val _pantry = MutableStateFlow(PantryDocument())
    val pantry: StateFlow<PantryDocument> = _pantry.asStateFlow()

    private val _memory = MutableStateFlow(Memory())
    val memory: StateFlow<Memory> = _memory.asStateFlow()

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _shopping = MutableStateFlow<ShoppingList?>(null)
    val shopping: StateFlow<ShoppingList?> = _shopping.asStateFlow()

    private val _collapsed = MutableStateFlow<Set<String>>(emptySet())

    /** The spaces folded away on this device. */
    val collapsedSpaces: StateFlow<Set<String>> = _collapsed.asStateFlow()

    private var device = DeviceState("")
    private lateinit var editor: PantryEditor
    private lateinit var listEditor: ListEditor
    private var lastUndo: UndoStep? = null
    private val saveLock = Mutex()
    private val syncLock = Mutex()
    private var pendingSync: Job? = null

    /** Reads everything from disk; later calls (a recreated screen, say) do nothing. */
    suspend fun load() {
        if (device.device.isNotEmpty()) return
        read(SETTINGS, Settings.serializer())?.let { _settings.value = it }
        read(MEMORY, Memory.serializer())?.let { _memory.value = it }
        files.read(PANTRY)?.let { text ->
            runCatching { PantryCodec.decode(text) }
                .onSuccess { _pantry.value = it }
                // Kept aside rather than overwritten by the next edit, so nothing is lost for good.
                .onFailure { files.write("pantry-unreadable-${now()}.json", text) }
        }
        device = read(DEVICE, DeviceState.serializer()) ?: DeviceState(newDeviceId()).also { saveDevice(it) }
        _collapsed.value = device.collapsedSpaces
        editor = PantryEditor(device.device, now)
        listEditor = ListEditor(device.device, now)
        val link = device.shoppingLink ?: return
        val doc = files.read(SHOPPING)?.let { runCatching { ListCodec.decode(it) }.getOrNull() } ?: ListDocument()
        _shopping.value = ShoppingList(
            link,
            doc,
            device.shoppingSyncedAt?.let { SyncState.Synced(it) } ?: SyncState.Syncing,
            device.shoppingCollapsed,
        )
    }

    suspend fun updateSettings(settings: Settings) {
        _settings.value = settings
        save(SETTINGS, Settings.serializer(), settings)
    }

    private fun liveSpaces(): Set<String> = _pantry.value.visibleSpaces().mapTo(HashSet()) { it.id }

    // Spaces

    fun addSpace(name: String): String {
        val (doc, id) = editor.addSpace(_pantry.value, name)
        setPantry(doc)
        return id
    }

    fun renameSpace(id: String, name: String) = setPantry(editor.renameSpace(_pantry.value, id, name))

    fun moveSpace(id: String, offset: Int) = setPantry(editor.moveSpace(_pantry.value, id, offset))

    fun deleteSpace(id: String) = setPantry(editor.deleteSpace(_pantry.value, id))

    /** Folds a space away on this device, or opens it. */
    fun setCollapsed(space: String, collapsed: Boolean) = setAllCollapsed(
        if (collapsed) _collapsed.value + space else _collapsed.value - space,
    )

    /** Folds exactly [spaces] away on this device. */
    fun setAllCollapsed(spaces: Set<String>) {
        _collapsed.value = spaces
        scope.launch { saveDevice(device.copy(collapsedSpaces = spaces)) }
    }

    // Food

    /** Puts food in a space and learns that it goes there. Returns what undoes it. */
    fun addItem(name: String, space: String, quantity: Int = 1, expires: LocalDate? = null): UndoStep =
        edit({ editor.addItem(it, name, space, quantity, expires).first }) {
            it.placed(name, space, now(), today(), expires)
        }

    /** Changes an item; a new space counts as moving it there. */
    fun editItem(id: String, name: String, quantity: Int, expires: LocalDate?, space: String): UndoStep? {
        val item = _pantry.value.item(id) ?: return null
        return edit({ doc ->
            val edited = editor.editItem(doc, id, name, quantity, expires)
            if (space != item.space) editor.moveItem(edited, id, space) else edited
        }) { memory ->
            if (space != item.space) memory.moved(name, item.space, space) else memory
        }
    }

    /** Moves [count] of an item to [space] and learns where that food goes from where it was. */
    fun moveItem(id: String, space: String, count: Int = Int.MAX_VALUE): UndoStep? {
        val item = _pantry.value.item(id) ?: return null
        if (item.space == space) return null
        return edit({ editor.moveItem(it, id, space, count) }) { it.moved(item.name, item.space, space) }
    }

    /** Takes one of an item. Returns what undoes it, and whether that was the last one. */
    fun useOne(id: String): Pair<UndoStep, Boolean>? {
        val item = _pantry.value.item(id) ?: return null
        return edit({ editor.useOne(it, id) }) to (item.quantity <= 1)
    }

    /** Takes an item out of the pantry, all of it: it is finished, thrown away, or was added by mistake. */
    fun removeItem(id: String): UndoStep? {
        _pantry.value.item(id) ?: return null
        return edit({ editor.removeItem(it, id) })
    }

    /** Takes back [step], if it is still the latest change. */
    fun undo(step: UndoStep) {
        if (step !== lastUndo) return
        lastUndo = null
        setPantry(editor.restore(_pantry.value, step.items))
        setMemory(step.memory)
    }

    private fun edit(change: (PantryDocument) -> PantryDocument, learn: (Memory) -> Memory = { it }): UndoStep {
        val before = _pantry.value
        val after = change(before)
        val step = UndoStep(changedItems(before, after), _memory.value)
        lastUndo = step
        setPantry(after)
        setMemory(learn(_memory.value))
        return step
    }

    // Suggestions

    /** The space [name] usually goes in; null if the app does not know yet. */
    fun placeFor(name: String): String? = _memory.value.placeFor(name, liveSpaces())

    /** Where [item] is probably going when it is moved; null if there is no clue. */
    fun moveFor(item: PantryItem): String? = _memory.value.moveFor(item.name, item.space, liveSpaces())

    /** Known foods [text] could be the start of, best first. */
    fun complete(text: String): List<Food> = _memory.value.complete(text)

    /** What the app has learned about [name], if anything. */
    fun food(name: String): Food? = _memory.value.food(name)

    /** Today, as the pantry counts days to expiry. */
    fun today(): LocalDate = date()

    // Receipts

    /**
     * Reads a receipt from the text found on its photos, taken top to bottom, and says what each product
     * line probably is.
     */
    fun understandReceipt(pages: List<List<OcrLine>>): List<ReceiptEntry> =
        Recognizer.recognize(ReceiptParser.parsePages(pages), _memory.value, liveSpaces(), today())

    /**
     * Puts the included entries in the pantry, and learns what each line was: the food it is, where
     * that goes, how long it keeps; and that lines left out are not for the pantry. Returns what undoes
     * it, and how many items were put away.
     */
    fun saveReceipt(entries: List<ReceiptEntry>): Pair<UndoStep, Int> {
        val spaces = liveSpaces()
        val kept = entries.filter { it.include && it.name.isNotBlank() && it.space in spaces }
        val step = edit({ doc ->
            kept.fold(doc) { d, e -> editor.addItem(d, e.name, e.space!!, e.quantity, e.expires).first }
        }) { memory ->
            entries.fold(memory) { m, e ->
                // Products the user added by hand have no receipt line to learn.
                val printed = receiptKey(e.line.text).isNotEmpty()
                when {
                    e in kept -> {
                        // 6 items for 1 printed: a pack of 6, remembered for that line.
                        val pack = if (e.quantity % e.line.quantity == 0) e.quantity / e.line.quantity else 1
                        (if (printed) m.receiptMeans(e.line.text, e.name, pack) else m)
                            .placed(e.name, e.space!!, now(), today(), e.expires)
                    }
                    !e.include && printed -> m.receiptSkips(e.line.text)
                    else -> m
                }
            }
        }
        return step to kept.size
    }

    // Shopping list

    /**
     * Links the Open Check Lists list behind [link]. Nothing changes unless the first sync works, so a
     * mistyped link or password is reported instead. A shared folder may hold several lists: with one
     * it is used, with more [ListChoiceNeeded] lists them, and the caller passes the chosen one's
     * [ShareLink.fileName].
     */
    suspend fun linkShoppingList(link: ShareLink) {
        var target = ShareLinks.normalize(link)
        val share = nextcloudShareFor(target, http)
        if (share != null && target.fileName == null) {
            share.resolve()
            if (share.isFolder) {
                val lists = share.lists()
                target = when (lists.size) {
                    0 -> throw RemoteException(Messages.current.noListsInFolder)
                    1 -> target.copy(fileName = lists.single().fileName)
                    else -> throw ListChoiceNeeded(target, lists)
                }
            }
        }
        pendingSync?.cancel()
        val doc = syncWith(remoteFileFor(target, http), ListDocument())
        _shopping.value = ShoppingList(target, doc, SyncState.Synced(now()))
        saveShoppingDoc()
        saveDevice(device.copy(shoppingLink = target, shoppingSyncedAt = now(), shoppingCollapsed = emptySet()))
    }

    /** Stops using the shopping list on this device; the list itself is left as it is. */
    suspend fun unlinkShoppingList() {
        pendingSync?.cancel()
        _shopping.value = null
        saveLock.withLock { files.delete(SHOPPING) }
        saveDevice(device.copy(shoppingLink = null, shoppingSyncedAt = null, shoppingCollapsed = emptySet()))
    }

    /** The sublist [name] went on last time, if it is still on the list. */
    fun sublistFor(name: String): Sublist? {
        val remembered = _memory.value.food(name)?.sublist ?: return null
        if (remembered.isEmpty()) return Sublist.None
        val doc = _shopping.value?.doc ?: return null
        return doc.visibleSections().firstOrNull { nameKey(it.name) == nameKey(remembered) }?.let { Sublist.Existing(it.id) }
    }

    /**
     * Puts [name] on the shopping list in [sublist], and remembers the sublist for next time. A ticked
     * item of that name there is unticked rather than added again.
     */
    fun addToShopping(name: String, sublist: Sublist) {
        val current = _shopping.value ?: return
        var doc = current.doc
        val section = when (sublist) {
            Sublist.None -> null
            is Sublist.Existing -> sublist.id
            is Sublist.New -> listEditor.addSection(doc, sublist.name).let { (withSection, id) -> doc = withSection; id }
        }
        doc = listEditor.putOnList(doc, section, name)
        val sectionName = section?.let { id -> doc.sections.firstOrNull { it.id == id }?.name }.orEmpty()
        setMemory(_memory.value.wentOnSublist(name, sectionName))
        changeShopping(doc)
    }

    /** Ticks or unticks an item of the shopping list. */
    fun setShoppingDone(id: String, done: Boolean) {
        val current = _shopping.value ?: return
        changeShopping(listEditor.setDone(current.doc, id, done))
    }

    fun setShoppingCollapsed(section: String, collapsed: Boolean) {
        val current = _shopping.value ?: return
        val folded = if (collapsed) current.collapsed + section else current.collapsed - section
        _shopping.value = current.copy(collapsed = folded)
        scope.launch { saveDevice(device.copy(shoppingCollapsed = folded)) }
    }

    private fun changeShopping(doc: ListDocument) {
        _shopping.update { it?.copy(doc = doc) }
        scope.launch { saveShoppingDoc() }
        scheduleSync(syncDelayMillis)
    }

    fun syncShopping() = scheduleSync(0)

    private fun scheduleSync(delayMillis: Long) {
        if (_shopping.value == null) return
        pendingSync?.cancel()
        pendingSync = scope.launch {
            delay(delayMillis)
            sync()
        }
    }

    /** Runs one sync of the shopping list; a failure is recorded on it, not thrown. */
    suspend fun sync() = syncLock.withLock {
        val current = _shopping.value ?: return@withLock
        _shopping.update { it?.copy(sync = SyncState.Syncing) }
        try {
            val merged = syncWith(remoteFileFor(current.link, http), current.doc.pruned(now()))
            var changedMeanwhile = false
            _shopping.update {
                if (it == null || it.link != current.link) return@update it
                val combined = merge(it.doc, merged)
                changedMeanwhile = combined != merged
                it.copy(doc = combined, sync = SyncState.Synced(now()))
            }
            saveShoppingDoc()
            saveDevice(device.copy(shoppingSyncedAt = now()))
            if (changedMeanwhile) scheduleSync(syncDelayMillis)
        } catch (e: RemoteException) {
            _shopping.update { if (it?.link == current.link) it.copy(sync = SyncState.Failed(e.message ?: Messages.current.syncFailed)) else it }
        }
    }

    // Storage

    private fun setPantry(doc: PantryDocument) {
        _pantry.value = doc
        scope.launch { saveLock.withLock { files.write(PANTRY, PantryCodec.encode(_pantry.value.pruned(now()))) } }
    }

    private fun setMemory(memory: Memory) {
        if (memory == _memory.value) return
        _memory.value = memory
        scope.launch { save(MEMORY, Memory.serializer(), _memory.value) }
    }

    private suspend fun saveShoppingDoc() {
        val doc = _shopping.value?.doc ?: return
        saveLock.withLock { files.write(SHOPPING, ListCodec.encode(doc)) }
    }

    private suspend fun saveDevice(state: DeviceState) {
        device = state
        save(DEVICE, DeviceState.serializer(), state)
    }

    private suspend fun <T> read(name: String, serializer: kotlinx.serialization.KSerializer<T>): T? =
        files.read(name)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

    private suspend fun <T> save(name: String, serializer: kotlinx.serialization.KSerializer<T>, value: T) {
        saveLock.withLock { files.write(name, json.encodeToString(serializer, value)) }
    }

    private fun newDeviceId(): String = buildString {
        repeat(8) { append("0123456789abcdef"[Random.nextInt(16)]) }
    }

    private companion object {
        const val PANTRY = "pantry.json"
        const val MEMORY = "memory.json"
        const val SETTINGS = "settings.json"
        const val DEVICE = "device.json"
        /** This device's copy of the shopping list, in Open Check Lists' format. */
        const val SHOPPING = "shopping-list.json"
        val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = false }
    }
}

/**
 * The platform's HTTP engine. It is named here, not looked up at run time through Java's ServiceLoader
 * as ktor otherwise does, so that code shrinkers (R8, ProGuard) see it is used and keep it.
 */
internal expect val httpEngine: HttpClientEngineFactory<*>

fun defaultHttpClient(): HttpClient = HttpClient(httpEngine) {
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 10_000
    }
}

fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

fun systemToday(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
