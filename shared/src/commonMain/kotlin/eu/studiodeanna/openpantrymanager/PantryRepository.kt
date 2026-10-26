package eu.studiodeanna.openpantrymanager

import eu.studiodeanna.openpantrymanager.checklist.Item
import eu.studiodeanna.openpantrymanager.checklist.ListAction
import eu.studiodeanna.openpantrymanager.checklist.ListChange
import eu.studiodeanna.openpantrymanager.checklist.ListCodec
import eu.studiodeanna.openpantrymanager.checklist.ListDocument
import eu.studiodeanna.openpantrymanager.checklist.ListEditor
import eu.studiodeanna.openpantrymanager.checklist.action
import eu.studiodeanna.openpantrymanager.checklist.listChangeBetween
import eu.studiodeanna.openpantrymanager.checklist.merge
import eu.studiodeanna.openpantrymanager.checklist.pending
import eu.studiodeanna.openpantrymanager.checklist.pruned
import eu.studiodeanna.openpantrymanager.checklist.similarItems
import eu.studiodeanna.openpantrymanager.checklist.visibleSections
import eu.studiodeanna.openpantrymanager.memory.Food
import eu.studiodeanna.openpantrymanager.memory.Memory
import eu.studiodeanna.openpantrymanager.memory.changedTo
import eu.studiodeanna.openpantrymanager.memory.combinedWith
import eu.studiodeanna.openpantrymanager.memory.complete
import eu.studiodeanna.openpantrymanager.memory.boxed
import eu.studiodeanna.openpantrymanager.memory.food
import eu.studiodeanna.openpantrymanager.memory.isBoxed
import eu.studiodeanna.openpantrymanager.memory.forgetPhrase
import eu.studiodeanna.openpantrymanager.memory.moveFor
import eu.studiodeanna.openpantrymanager.memory.moved
import eu.studiodeanna.openpantrymanager.memory.nextStamp
import eu.studiodeanna.openpantrymanager.memory.phraseMeans
import eu.studiodeanna.openpantrymanager.memory.placeFor
import eu.studiodeanna.openpantrymanager.memory.placed
import eu.studiodeanna.openpantrymanager.memory.receiptKey
import eu.studiodeanna.openpantrymanager.memory.receiptMeans
import eu.studiodeanna.openpantrymanager.memory.receiptSkips
import eu.studiodeanna.openpantrymanager.memory.reverted
import eu.studiodeanna.openpantrymanager.memory.wentOnList
import eu.studiodeanna.openpantrymanager.memory.withSpaces
import eu.studiodeanna.openpantrymanager.model.History
import eu.studiodeanna.openpantrymanager.model.PantryAction
import eu.studiodeanna.openpantrymanager.model.PantryChange
import eu.studiodeanna.openpantrymanager.model.PantryCodec
import eu.studiodeanna.openpantrymanager.model.PantryDocument
import eu.studiodeanna.openpantrymanager.model.PantryEditor
import eu.studiodeanna.openpantrymanager.model.PantryItem
import eu.studiodeanna.openpantrymanager.model.PantryStep
import eu.studiodeanna.openpantrymanager.model.UndoState
import eu.studiodeanna.openpantrymanager.model.compareStamps
import eu.studiodeanna.openpantrymanager.model.item
import eu.studiodeanna.openpantrymanager.model.liveItems
import eu.studiodeanna.openpantrymanager.model.mergeStamped
import eu.studiodeanna.openpantrymanager.model.nameKey
import eu.studiodeanna.openpantrymanager.model.pantryChangeBetween
import eu.studiodeanna.openpantrymanager.model.pruned
import eu.studiodeanna.openpantrymanager.model.space
import eu.studiodeanna.openpantrymanager.model.state
import eu.studiodeanna.openpantrymanager.model.unopened
import eu.studiodeanna.openpantrymanager.model.visibleSpaces
import eu.studiodeanna.openpantrymanager.receipt.OcrLine
import eu.studiodeanna.openpantrymanager.receipt.ReceiptEntry
import eu.studiodeanna.openpantrymanager.receipt.ReceiptParser
import eu.studiodeanna.openpantrymanager.receipt.Recognizer
import eu.studiodeanna.openpantrymanager.store.DeviceState
import eu.studiodeanna.openpantrymanager.store.FileStore
import eu.studiodeanna.openpantrymanager.store.Settings
import eu.studiodeanna.openpantrymanager.sync.NextcloudPublicFile
import eu.studiodeanna.openpantrymanager.sync.PantryFormat
import eu.studiodeanna.openpantrymanager.sync.RemoteException
import eu.studiodeanna.openpantrymanager.sync.ShareLink
import eu.studiodeanna.openpantrymanager.sync.ShareLinks
import eu.studiodeanna.openpantrymanager.sync.SharedList
import eu.studiodeanna.openpantrymanager.sync.SharedPantry
import eu.studiodeanna.openpantrymanager.sync.SharedPantryCodec
import eu.studiodeanna.openpantrymanager.sync.SharedShoppingList
import eu.studiodeanna.openpantrymanager.sync.merge
import eu.studiodeanna.openpantrymanager.sync.nextcloudShareFor
import eu.studiodeanna.openpantrymanager.sync.remoteFileFor
import eu.studiodeanna.openpantrymanager.sync.syncDocument
import eu.studiodeanna.openpantrymanager.sync.syncWith
import eu.studiodeanna.openpantrymanager.voice.VoiceAction
import eu.studiodeanna.openpantrymanager.voice.VoiceCommand
import eu.studiodeanna.openpantrymanager.voice.VoiceLanguage
import eu.studiodeanna.openpantrymanager.voice.phraseToLearn
import eu.studiodeanna.openpantrymanager.voice.understandVoice
import eu.studiodeanna.openpantrymanager.voice.voiceVocabulary
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import kotlin.random.Random
import kotlin.time.Clock
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
 * What taking food did: called [action], undone by [step]; [left] of the item stay in the pantry.
 * [ranOut]: none of that food is left unopened now (the last one finished, the last box opened), so
 * it may go on the shopping list.
 */
data class Taken(val action: PantryAction, val step: UndoStep?, val left: Int, val ranOut: Boolean)

/** The shared pantry this device is in, and how syncing it goes. */
data class PantrySharing(val link: ShareLink, val sync: SyncState)

/** Thrown by [PantryRepository.sharePantry] when a shared folder holds several pantries to choose from. */
class PantryChoiceNeeded(val link: ShareLink, val pantries: List<SharedList>) : RemoteException(Messages.current.choosePantry)

/**
 * Thrown by [PantryRepository.sharePantry] when the shared pantry exists already and this device's has
 * spaces or food of its own: the user says how the two become one, and the call is made again.
 */
class JoinChoiceNeeded(val link: ShareLink) : RemoteException("")

/** How a device's own pantry joins a shared one that exists already. */
enum class JoinMode {
    /** Its spaces and food join the shared pantry, and what it learned; spaces of the same name become one. */
    Merge,

    /** The shared pantry replaces it; the device's own is kept aside in a file. */
    Replace,
}

/** An edit of the pantry made on this device, which the user can take back. */
typealias UndoStep = PantryStep

/**
 * The pantry on this device, what the app has learned, and the linked shopping list. Edits are saved
 * at once. The shopping list, and the pantry when it is shared ([sharePantry]), sync shortly after
 * each change and whenever [syncNow] is called.
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

    private val _undo = MutableStateFlow(UndoState())

    /** Whether this device's edits of the pantry can be undone or redone. */
    val undoState: StateFlow<UndoState> = _undo.asStateFlow()

    private val _shoppingUndo = MutableStateFlow(UndoState())

    /** Whether this device's edits of the shopping list can be undone or redone. */
    val shoppingUndoState: StateFlow<UndoState> = _shoppingUndo.asStateFlow()

    private val _sharing = MutableStateFlow<PantrySharing?>(null)

    /** The shared pantry this device is in; null while the pantry is this device's alone. */
    val sharing: StateFlow<PantrySharing?> = _sharing.asStateFlow()

    private var device = DeviceState("")
    private lateinit var editor: PantryEditor
    private lateinit var listEditor: ListEditor
    private var history = History<PantryChange, PantryAction>()
        set(value) {
            field = value
            _undo.value = value.state
        }
    private var shoppingHistory = History<ListChange, ListAction>()
        set(value) {
            field = value
            _shoppingUndo.value = value.state
        }
    private val saveLock = Mutex()
    private val syncLock = Mutex()
    private var pendingSync: Job? = null
    private val pantryLock = Mutex()
    private var pendingPantrySync: Job? = null

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
        device.pantryLink?.let { link ->
            _sharing.value = PantrySharing(link, device.pantrySyncedAt?.let { SyncState.Synced(it) } ?: SyncState.Syncing)
        }
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
        var id = ""
        edit(PantryAction.AddedSpace(name.trim()), { doc -> editor.addSpace(doc, name).also { id = it.second }.first })
        return id
    }

    fun renameSpace(id: String, name: String) {
        edit(PantryAction.RenamedSpace(name.trim()), { editor.renameSpace(it, id, name) })
    }

    fun moveSpace(id: String, offset: Int) {
        val space = _pantry.value.space(id) ?: return
        edit(PantryAction.MovedSpace(space.name), { editor.moveSpace(it, id, offset) })
    }

    /** Deletes a space and the food in it. Returns what undoes it. */
    fun deleteSpace(id: String): UndoStep? {
        val space = _pantry.value.space(id) ?: return null
        return edit(PantryAction.DeletedSpace(space.name), { editor.deleteSpace(it, id) })
    }

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

    /**
     * Puts food in a space, bought on [bought] (today, unless said otherwise; null if not known), in
     * closed boxes if [boxed] (null: as that food came last time), and learns that it goes there, whether
     * it comes in boxes and, with an [expires] day, how long it keeps. Returns what undoes it.
     */
    fun addItem(
        name: String,
        space: String,
        quantity: Int = 1,
        expires: LocalDate? = null,
        bought: LocalDate? = today(),
        boxed: Boolean? = null,
    ): UndoStep? {
        val inBoxes = boxed ?: _memory.value.isBoxed(name)
        return edit(PantryAction.AddedFood(name.trim()), { editor.addItem(it, name, space, quantity, expires, bought, inBoxes).first }) {
            val placed = it.placed(name, space, now(), today(), expires, bought)
            if (boxed != null) placed.boxed(name, boxed) else placed
        }
    }

    /**
     * Changes an item, and whether it comes in boxes and since when they are open (null: closed); a new
     * space counts as moving it there. Opening its last closed box, the food may go on the shopping list.
     */
    fun editItem(
        id: String,
        name: String,
        quantity: Int,
        expires: LocalDate?,
        space: String,
        bought: LocalDate? = null,
        boxed: Boolean = _pantry.value.item(id)?.boxed ?: false,
        opened: LocalDate? = _pantry.value.item(id)?.opened,
    ): Taken? {
        val item = _pantry.value.item(id) ?: return null
        val before = _pantry.value
        val action = PantryAction.EditedFood(name.trim())
        val step = edit(action, { doc ->
            val edited = editor.editItem(doc, id, name, quantity, expires, bought, boxed, opened)
            if (space != item.space) editor.moveItem(edited, id, space) else edited
        }) { memory ->
            val moved = if (space != item.space) memory.moved(name, item.space, space) else memory
            if (boxed != item.boxed) moved.boxed(name, boxed) else moved
        }
        val after = _pantry.value
        val openedNow = item.opened == null && boxed && opened != null && nameKey(name) == nameKey(item.name)
        return Taken(action, step, quantity, openedNow && ranOut(item.name, before, after))
    }

    /** Moves [count] of an item to [space] and learns where that food goes from where it was. */
    fun moveItem(id: String, space: String, count: Int = Int.MAX_VALUE): UndoStep? {
        val item = _pantry.value.item(id) ?: return null
        if (item.space == space) return null
        return edit(PantryAction.MovedFood(item.name), { editor.moveItem(it, id, space, count) }) {
            it.moved(item.name, item.space, space)
        }
    }

    /**
     * What a tap on an item's count does: a closed box is opened (see [open]); anything else, one is
     * taken, the last one out of the pantry.
     */
    fun useOne(id: String): Taken? {
        val item = _pantry.value.item(id) ?: return null
        return if (item.boxed && item.opened == null) open(id) else useSome(id, 1)
    }

    /** Takes [count] of an item, whole boxes too; all of it when that is its quantity or more. */
    fun useSome(id: String, count: Int): Taken? {
        val item = _pantry.value.item(id) ?: return null
        val action = when {
            item.quantity <= count -> PantryAction.Finished(item.name)
            count == 1 -> PantryAction.UsedOne(item.name)
            else -> PantryAction.UsedSome(item.name, count)
        }
        return taking(item, action, { editor.useSome(it, id, count) })
    }

    /**
     * Opens [count] closed boxes of an item today: they are listed apart, as opened, and the next tap
     * takes them out. Food not in boxes so far comes in boxes from now on, here and when bought again.
     */
    fun open(id: String, count: Int = 1): Taken? {
        val item = _pantry.value.item(id)?.takeIf { it.opened == null } ?: return null
        return taking(item, PantryAction.Opened(item.name), { editor.open(it, id, today(), count).first }) { it.boxed(item.name, true) }
    }

    /** Takes all of an item out of the pantry: it is finished. */
    fun finish(id: String): Taken? {
        val item = _pantry.value.item(id) ?: return null
        return taking(item, PantryAction.Finished(item.name), { editor.removeItem(it, id) })
    }

    /** Takes some of [item] with [change], called [action], and says what that did. */
    private fun taking(item: PantryItem, action: PantryAction, change: (PantryDocument) -> PantryDocument, learn: (Memory) -> Memory = { it }): Taken {
        val before = _pantry.value
        val step = edit(action, change, learn)
        val after = _pantry.value
        return Taken(action, step, after.item(item.id)?.quantity ?: 0, ranOut(item.name, before, after))
    }

    /** Whether going from [before] to [after] left none of the food [name] unopened, where there was some. */
    private fun ranOut(name: String, before: PantryDocument, after: PantryDocument): Boolean =
        before.unopened(name) > 0 && after.unopened(name) == 0

    /** What was [heard] in [language] (in more ways than one, the likeliest first) asks to do; nothing is done yet. */
    fun understand(heard: List<String>, language: VoiceLanguage): VoiceCommand =
        understandVoice(heard, language, _pantry.value, _memory.value)

    /** The words for the speech recognizer to listen for in [language]: of commands, and of the food here. */
    fun voiceVocabulary(language: VoiceLanguage): List<String> = voiceVocabulary(language, _pantry.value, _memory.value)

    /** The words of [heard], a sentence not understood, that the user could teach the app the meaning of; see [phraseToLearn]. */
    fun phraseToLearn(heard: String, language: VoiceLanguage): String? = phraseToLearn(heard, language, _pantry.value, _memory.value)

    /**
     * Learns that [phrase], said in [language], means [action], from now on and, in a shared pantry,
     * on every phone sharing it.
     */
    fun learnPhrase(language: VoiceLanguage, phrase: String, action: VoiceAction) =
        learnNow(_memory.value.phraseMeans(language.code, phrase, action))

    /** Forgets a phrase taught in [language], here and on every phone sharing the pantry. */
    fun forgetPhrase(language: VoiceLanguage, phrase: String) = learnNow(_memory.value.forgetPhrase(language.code, phrase))

    /**
     * Takes an item out of the pantry, all of it: thrown away, or added by mistake ([finish] when it is
     * finished). Returns what undoes it.
     */
    fun removeItem(id: String): UndoStep? {
        val item = _pantry.value.item(id) ?: return null
        return edit(PantryAction.DeletedFood(item.name), { editor.removeItem(it, id) })
    }

    /**
     * Takes back this device's latest edit of the pantry, and what the app learned from it, as a new
     * edit that syncs like any other. What other devices of a shared pantry changed since stays; an edit
     * they changed entirely is skipped for the one before it. Returns what was undone, or null.
     */
    fun undo(): PantryAction? = replay(redo = false)

    /** Makes the latest undone edit again, the way [undo] takes one back. */
    fun redo(): PantryAction? = replay(redo = true)

    /** Undoes [step] if it is still the latest edit: the Undo of a message about it. */
    fun undo(step: UndoStep): PantryAction? = if (history.undo.lastOrNull() === step) undo() else null

    private fun replay(redo: Boolean): PantryAction? {
        while (true) {
            val step = history.next(redo) ?: return null
            val doc = _pantry.value
            val known = _memory.value
            val reverted = editor.revert(doc, step.change)
            val learned = known.reverted(step.change.learned, stamp())
            val back = pantryChangeBetween(doc, reverted, known, learned)
            history = history.took(redo, back)
            if (back != null) {
                setPantry(reverted)
                setMemory(learned)
                return step.action
            }
        }
    }

    /**
     * Makes an edit of the pantry, called [action], and learns from it; it can be undone. Returns what
     * undoes it; null if it changed nothing, and nothing is saved then.
     */
    private fun edit(action: PantryAction, change: (PantryDocument) -> PantryDocument, learn: (Memory) -> Memory = { it }): UndoStep? {
        val before = _pantry.value
        val after = change(before)
        val known = _memory.value
        val learned = known.changedTo(learn(known), stamp())
        val diff = pantryChangeBetween(before, after, known, learned) ?: return null
        history = history.record(diff, action)
        setPantry(after)
        setMemory(learned)
        return history.undo.last()
    }

    /** What the app knows now: [learned], which it became on this device, stamped where it changed. */
    private fun learnNow(learned: Memory) = setMemory(_memory.value.changedTo(learned, stamp()))

    private fun stamp() = _memory.value.nextStamp(now(), device.device)

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
     * line probably is, and until when it keeps if bought on [bought].
     */
    fun understandReceipt(pages: List<List<OcrLine>>, bought: LocalDate = today()): List<ReceiptEntry> =
        Recognizer.recognize(ReceiptParser.parsePages(pages), _memory.value, liveSpaces(), bought)

    /**
     * Puts the included entries in the pantry, bought on [bought], and learns what each line was: the
     * food it is, where that goes, how long it keeps; and that lines left out are not for the pantry.
     * Returns what undoes it, and how many items were put away.
     */
    fun saveReceipt(entries: List<ReceiptEntry>, bought: LocalDate? = today()): Pair<UndoStep?, Int> {
        val spaces = liveSpaces()
        val kept = entries.filter { it.include && it.name.isNotBlank() && it.space in spaces }
        val known = _memory.value
        val step = edit(PantryAction.PutAway(kept.sumOf { it.quantity }), { doc ->
            kept.fold(doc) { d, e -> editor.addItem(d, e.name, e.space!!, e.quantity, e.expires, bought, known.isBoxed(e.name)).first }
        }) { memory ->
            entries.fold(memory) { m, e ->
                // Products the user added by hand have no receipt line to learn.
                val printed = receiptKey(e.line.text).isNotEmpty()
                when {
                    e in kept -> {
                        // 6 items for 1 printed: a pack of 6, remembered for that line.
                        val pack = if (e.quantity % e.line.quantity == 0) e.quantity / e.line.quantity else 1
                        (if (printed) m.receiptMeans(e.line.text, e.name, pack) else m)
                            .placed(e.name, e.space!!, now(), today(), e.expires, bought)
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
        shoppingHistory = History()
        _shopping.value = ShoppingList(target, doc, SyncState.Synced(now()))
        saveShoppingDoc()
        saveDevice(device.copy(shoppingLink = target, shoppingSyncedAt = now(), shoppingCollapsed = emptySet()).linkStamped())
        schedulePantrySync(0)
    }

    /**
     * Stops using the shopping list on this device, and on the others of a shared pantry; the list
     * itself is left as it is.
     */
    suspend fun unlinkShoppingList() {
        pendingSync?.cancel()
        shoppingHistory = History()
        _shopping.value = null
        saveLock.withLock { files.delete(SHOPPING) }
        saveDevice(device.copy(shoppingLink = null, shoppingSyncedAt = null, shoppingCollapsed = emptySet()).linkStamped())
        schedulePantrySync(0)
    }

    /** This state with its shopping list stamped as changed on this device now, for a shared pantry to pass on. */
    private fun DeviceState.linkStamped() = copy(shoppingLinkAt = maxOf(now(), shoppingLinkAt + 1), shoppingLinkBy = device)

    /** The sublist [name] went on last time, if it is still on the list. */
    fun sublistFor(name: String): Sublist? {
        val remembered = _memory.value.food(name)?.sublist ?: return null
        if (remembered.isEmpty()) return Sublist.None
        val doc = _shopping.value?.doc ?: return null
        return doc.visibleSections().firstOrNull { nameKey(it.name) == nameKey(remembered) }?.let { Sublist.Existing(it.id) }
    }

    /** What [food] is called on the shopping list: what the user called it there before, else its own name. */
    fun shoppingNameFor(food: String): String = _memory.value.food(food)?.let { it.shoppingName ?: it.name } ?: food.trim()

    /** Items of the shopping list that may already be [text], ticked or not: the same name, or a similar one. */
    fun similarOnList(text: String): List<Item> = _shopping.value?.doc?.similarItems(text).orEmpty()

    /**
     * Puts [food] on the shopping list as [text] (its name there), in [sublist], and remembers both for
     * next time. An item of that name still to buy is left as it is, and a ticked one there is unticked,
     * rather than added again.
     */
    fun addToShopping(food: String, text: String, sublist: Sublist) {
        val current = _shopping.value ?: return
        current.doc.pending(text)?.let { return useShoppingItem(food, it.id) }
        var doc = current.doc
        val section = when (sublist) {
            Sublist.None -> null
            is Sublist.Existing -> sublist.id
            is Sublist.New -> listEditor.addSection(doc, sublist.name).let { (withSection, id) -> doc = withSection; id }
        }
        doc = listEditor.putOnList(doc, section, text)
        val sectionName = section?.let { id -> doc.sections.firstOrNull { it.id == id }?.name }.orEmpty()
        learnNow(_memory.value.wentOnList(food, text, sectionName))
        changeShopping(doc, ListAction.AddedItem(text.trim()))
    }

    /** Puts [name] on the shopping list in [sublist], as itself; see the other [addToShopping]. */
    fun addToShopping(name: String, sublist: Sublist) = addToShopping(name, name, sublist)

    /**
     * Takes the shopping list's item [id] for [food]: it goes back on the list if it was ticked, and is
     * what [food] is called there, in its sublist, from now on.
     */
    fun useShoppingItem(food: String, id: String) {
        val doc = _shopping.value?.doc ?: return
        val item = doc.items.firstOrNull { it.id == id && !it.deleted } ?: return
        val sectionName = item.section?.let { section -> doc.visibleSections().firstOrNull { it.id == section }?.name }.orEmpty()
        learnNow(_memory.value.wentOnList(food, item.text, sectionName))
        if (item.done) changeShopping(listEditor.setDone(doc, id, false))
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

    /**
     * Takes back this device's latest edit of the shopping list, as a new edit that syncs like any
     * other; what others changed since stays. Returns what was undone, or null.
     */
    fun undoShopping(): ListAction? = replayShopping(redo = false)

    /** Makes the latest undone edit of the shopping list again. */
    fun redoShopping(): ListAction? = replayShopping(redo = true)

    private fun replayShopping(redo: Boolean): ListAction? {
        while (true) {
            val step = shoppingHistory.next(redo) ?: return null
            val doc = _shopping.value?.doc ?: return null
            val reverted = listEditor.revert(doc, step.change)
            val back = listChangeBetween(doc, reverted)
            shoppingHistory = shoppingHistory.took(redo, back)
            if (back != null) {
                showShopping(reverted)
                return step.action
            }
        }
    }

    /** An edit of the shopping list made here, which can be undone: called [action], or as what it changed. */
    private fun changeShopping(doc: ListDocument, action: ListAction? = null) {
        val change = _shopping.value?.doc?.let { listChangeBetween(it, doc) } ?: return
        shoppingHistory = shoppingHistory.record(change, action ?: change.action())
        showShopping(doc)
    }

    private fun showShopping(doc: ListDocument) {
        _shopping.update { it?.copy(doc = doc) }
        scope.launch { saveShoppingDoc() }
        scheduleSync(syncDelayMillis)
    }

    /** Syncs the shopping list, and the pantry when it is shared, now: when the app is opened again, say. */
    fun syncNow() {
        schedulePantrySync(0)
        scheduleSync(0)
    }

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

    // Sharing the pantry

    /**
     * Shares the pantry, and what the app has learned, through the Nextcloud folder or file behind
     * [link], with every device that uses the same link. In a folder without a pantry, this device's is
     * saved there, in a file called [name] (".pantry" added); a pantry already there is joined. Joining
     * when this device has spaces or food of its own, [JoinChoiceNeeded] asks how they come together,
     * unless [join] says. A folder holding several pantries makes [PantryChoiceNeeded] list them; the
     * caller passes the chosen one's [ShareLink.fileName]. Nothing changes here unless it all works.
     */
    suspend fun sharePantry(link: ShareLink, name: String, join: JoinMode? = null) {
        var target = ShareLinks.normalize(link)
        val share = nextcloudShareFor(target, http) ?: run {
            // Says what is wrong with other kinds of links.
            remoteFileFor(target, http)
            throw RemoteException(Messages.current.notAShareLink)
        }
        share.resolve()
        // A list linked before pantries were shared gets a stamp now, to be passed on.
        if (device.shoppingLink != null && device.shoppingLinkAt == 0L) saveDevice(device.linkStamped())
        if (share.isFolder && target.fileName == null) {
            val pantries = share.pantries()
            when (pantries.size) {
                0 -> {
                    val mine = localShared()
                    val fileName = share.createFile(name, SharedPantry.EXTENSION, SharedPantryCodec.encode(mine))
                    startSharing(target.copy(fileName = fileName), mine)
                    return
                }
                1 -> target = target.copy(fileName = pantries.single().fileName)
                else -> throw PantryChoiceNeeded(target, pantries)
            }
        }
        val remote = NextcloudPublicFile(share, target.fileName)
        val shared = remote.read().text?.takeIf { it.isNotBlank() }?.let(SharedPantryCodec::decode)
        val start = when {
            shared == null -> localShared()
            join == JoinMode.Replace -> {
                keepAside()
                shared.copy(shoppingList = householdList(shared))
            }
            join == JoinMode.Merge || !hasOwnPantry() -> joinedWith(shared)
            else -> throw JoinChoiceNeeded(target)
        }
        pendingPantrySync?.cancel()
        startSharing(target, syncDocument(remote, start, PantryFormat))
    }

    /**
     * Stops sharing the pantry on this device, which keeps the pantry as it is now; the shared file
     * stays, for the other devices.
     */
    suspend fun stopSharingPantry() {
        pendingPantrySync?.cancel()
        _sharing.value = null
        saveDevice(device.copy(pantryLink = null, pantrySyncedAt = null))
    }

    /** Runs one sync of the shared pantry; a failure is recorded on [sharing], not thrown. */
    suspend fun syncPantry() = pantryLock.withLock {
        val link = device.pantryLink ?: return@withLock
        _sharing.update { it?.copy(sync = SyncState.Syncing) }
        try {
            val merged = syncDocument(remoteFileFor(link, http), localShared(), PantryFormat)
            if (device.pantryLink != link) return@withLock
            // What was changed here meanwhile stays, and goes out with the next round.
            val combined = localShared().merge(merged)
            show(combined)
            adoptShoppingList(combined.shoppingList)
            _sharing.update { if (it?.link == link) it.copy(sync = SyncState.Synced(now())) else it }
            saveDevice(device.copy(pantrySyncedAt = now()))
            if (combined != merged) schedulePantrySync(syncDelayMillis)
        } catch (e: RemoteException) {
            _sharing.update { if (it?.link == link) it.copy(sync = SyncState.Failed(e.message ?: Messages.current.syncFailed)) else it }
        }
    }

    private fun schedulePantrySync(delayMillis: Long) {
        if (device.pantryLink == null) return
        pendingPantrySync?.cancel()
        pendingPantrySync = scope.launch {
            delay(delayMillis)
            syncPantry()
        }
    }

    /** The pantry, what was learned and the shopping list, as this device would share them. */
    private fun localShared(): SharedPantry {
        val doc = _pantry.value.pruned(now())
        return SharedPantry(
            spaces = doc.spaces,
            items = doc.items,
            memory = _memory.value,
            shoppingList = if (device.shoppingLinkAt > 0) SharedShoppingList(device.shoppingLink, device.shoppingLinkAt, device.shoppingLinkBy) else null,
        )
    }

    private fun hasOwnPantry(): Boolean = _pantry.value.visibleSpaces().isNotEmpty() || _pantry.value.liveItems().isNotEmpty()

    /**
     * This device's pantry joined to [shared]: its spaces become the shared spaces of the same name,
     * the others are added; its food is added; what it learned adds to what the shared pantry knows.
     */
    private fun joinedWith(shared: SharedPantry): SharedPantry {
        val mine = _pantry.value
        val theirs = shared.pantry.visibleSpaces()
        val ids = mine.visibleSpaces().mapNotNull { space ->
            theirs.firstOrNull { nameKey(it.name) == nameKey(space.name) }?.let { space.id to it.id }
        }.toMap()
        val stamp = maxOf(shared.memory.nextStamp(now(), device.device), stamp(), compareBy { it.at })
        return SharedPantry(
            spaces = mergeStamped(shared.spaces, mine.spaces.filterNot { it.id in ids }),
            items = mergeStamped(shared.items, mine.items.map { if (it.space in ids) it.copy(space = ids.getValue(it.space)) else it }),
            memory = shared.memory.changedTo(shared.memory.combinedWith(_memory.value.withSpaces(ids)), stamp),
            shoppingList = householdList(shared),
        )
    }

    /** The shopping list of a pantry this device joins: the household's, or this device's if it has none. */
    private fun householdList(shared: SharedPantry): SharedShoppingList? {
        shared.shoppingList?.takeIf { it.link != null }?.let { return it }
        val mine = device.shoppingLink ?: return shared.shoppingList
        return SharedShoppingList(mine, maxOf(now(), device.shoppingLinkAt, (shared.shoppingList?.at ?: 0) + 1), device.device)
    }

    /** Keeps this device's pantry and what it learned in files of their own, before a shared pantry replaces them. */
    private suspend fun keepAside() {
        val time = now()
        saveLock.withLock {
            files.write("pantry-before-sharing-$time.json", PantryCodec.encode(_pantry.value))
            files.write("memory-before-sharing-$time.json", json.encodeToString(Memory.serializer(), _memory.value))
        }
    }

    private suspend fun startSharing(link: ShareLink, shared: SharedPantry) {
        pendingPantrySync?.cancel()
        // Undoing would now take back food that may be another device's.
        history = History()
        show(shared)
        _sharing.value = PantrySharing(link, SyncState.Synced(now()))
        saveDevice(device.copy(pantryLink = link, pantrySyncedAt = now()))
        adoptShoppingList(shared.shoppingList)
    }

    /**
     * Follows the shared pantry's shopping list when another device linked or unlinked one after this
     * device last did.
     */
    private suspend fun adoptShoppingList(shared: SharedShoppingList?) {
        if (shared == null || compareStamps(shared.at, shared.by, device.shoppingLinkAt, device.shoppingLinkBy) <= 0) return
        if (shared.link == device.shoppingLink) {
            saveDevice(device.copy(shoppingLinkAt = shared.at, shoppingLinkBy = shared.by))
            return
        }
        pendingSync?.cancel()
        shoppingHistory = History()
        saveLock.withLock { files.delete(SHOPPING) }
        _shopping.value = shared.link?.let { ShoppingList(it, ListDocument(), SyncState.Syncing) }
        saveDevice(
            device.copy(
                shoppingLink = shared.link,
                shoppingSyncedAt = null,
                shoppingCollapsed = emptySet(),
                shoppingLinkAt = shared.at,
                shoppingLinkBy = shared.by,
            ),
        )
        scheduleSync(0)
    }

    // Storage

    /** The pantry as changed on this device: saved, and passed on to the shared pantry shortly. */
    private fun setPantry(doc: PantryDocument) {
        showPantry(doc)
        schedulePantrySync(syncDelayMillis)
    }

    /** What the app knows as changed on this device: saved, and passed on to the shared pantry shortly. */
    private fun setMemory(memory: Memory) {
        if (memory == _memory.value) return
        showMemory(memory)
        schedulePantrySync(syncDelayMillis)
    }

    /** The shared pantry as synced: shown and saved, with nothing to pass on. */
    private fun show(shared: SharedPantry) {
        showPantry(shared.pantry)
        if (shared.memory != _memory.value) showMemory(shared.memory)
    }

    private fun showPantry(doc: PantryDocument) {
        _pantry.value = doc
        scope.launch { saveLock.withLock { files.write(PANTRY, PantryCodec.encode(_pantry.value.pruned(now()))) } }
    }

    private fun showMemory(memory: Memory) {
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
