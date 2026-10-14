package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import eu.studiodeanna.openpantrymanager.EnglishMessages
import eu.studiodeanna.openpantrymanager.ItalianMessages
import eu.studiodeanna.openpantrymanager.Messages
import eu.studiodeanna.openpantrymanager.checklist.ListAction
import eu.studiodeanna.openpantrymanager.model.PantryAction

/** The UI's texts in one language; [messages] are the matching ones raised by the shared code. */
interface Strings {
    val messages: Messages

    val appName: String get() = "Open Pantry Manager"
    val back: String
    val more: String
    val fewer: String
    val cancel: String
    val close: String
    val ok: String
    val save: String
    val delete: String
    val rename: String
    val add: String
    val name: String
    val undo: String
    val redo: String
    /** Shown after undoing [action], like "Undone: used one “Milk”"; the same for redoing. */
    fun undone(action: PantryAction): String
    fun redone(action: PantryAction): String
    fun undone(action: ListAction): String
    fun redone(action: ListAction): String
    val notNow: String

    // The pantry
    val pantry: String
    val search: String
    val searchFood: String
    val closeSearch: String
    fun noFoodMatches(query: String): String
    val scanReceipt: String
    val shoppingList: String
    val settings: String
    val addSpace: String
    val newSpace: String
    val sortByExpiry: String
    val sortByName: String
    val expandAll: String
    val collapseAll: String
    val addFood: String
    val addHere: String
    val doneAdding: String
    val whereFoodIs: String
    val whereFoodIsHelp: String
    /** One-tap names for the first spaces. */
    val commonSpaces: List<String>
    val otherSpace: String
    val useSoon: String
    val withoutSpace: String
    val spaceOptions: String
    val expandSpace: String
    val collapseSpace: String
    val moveUp: String
    val moveDown: String
    val deleteSpace: String
    val renameSpace: String
    val spaceNameHint: String
    fun deleteTitle(name: String): String
    val deleteSpaceMessage: String
    fun usedOne(name: String): String
    val readingReceipt: String
    val nothingOnReceipt: String
    val scanFailed: String

    // What was done, said in a snackbar
    fun addedTo(name: String, space: String): String
    fun movedTo(name: String, space: String): String
    fun finishedMessage(name: String): String
    fun left(name: String, count: Int): String
    fun deleted(name: String): String
    fun onShoppingList(name: String): String
    fun putAway(count: Int): String

    // Food dialogs
    val food: String
    val where: String
    fun usuallyIn(space: String): String
    val howMany: String
    val expires: String
    val noDate: String
    val pickDate: String
    fun usuallyKeeps(date: String): String
    val editFood: String
    val finished: String
    fun moveTitle(name: String): String
    val move: String
    val noOtherSpace: String
    val starMeansUsual: String
    fun allOf(count: Int): String

    // Expiry
    /** Short month names, January first. */
    val months: List<String>
    fun expired(daysAgo: Int): String
    fun expiresIn(days: Int): String
    fun expiresOn(date: String): String

    // Shopping list
    fun finishedTitle(name: String): String
    val linkToAddFinished: String
    val addToShopping: String
    val nameOnList: String
    val alreadyOnListHeading: String
    fun listItemChoice(text: String, sublist: String?, ticked: Boolean): String
    val addAsNew: String
    fun alreadyOnShoppingList(name: String): String
    fun putOnShoppingList(name: String): String
    fun onList(title: String): String
    val sublist: String
    val noSublist: String
    val newSublist: String
    val sublistName: String
    val linkShoppingList: String
    val linkIntro: String
    val shareLink: String
    val sharePassword: String
    val link: String
    val whichList: String
    val severalLists: String
    val noShoppingListTitle: String
    val noShoppingListHelp: String
    val linkAnother: String
    val unlink: String
    val unlinkTitle: String
    val unlinkMessage: String
    val syncNow: String
    val retry: String
    val syncing: String
    val syncedJustNow: String
    fun syncedMinutesAgo(minutes: Long): String
    fun syncedHoursAgo(hours: Long): String
    fun syncedDaysAgo(days: Long): String

    // Sharing the pantry
    val sharePantry: String
    val useSharedPantry: String
    val sharePantryIntro: String
    val share: String
    val whichPantry: String
    val severalPantries: String
    val joinTitle: String
    val joinMessage: String
    val joinMerge: String
    val joinReplace: String
    val sharedPantry: String
    val notShared: String
    fun sharedAs(file: String): String
    val addressForOthers: String
    val stopSharing: String
    fun sharedPantryProblem(message: String): String

    // Receipts
    val receipt: String
    val photoHint: String
    val takePhoto: String
    val takeAnotherPhoto: String
    val fromGallery: String
    val readReceipt: String
    val readingPhoto: String
    fun linesRead(count: Int): String
    fun photoNumber(number: Int): String
    fun removePhoto(number: Int): String
    val discardReceipt: String
    fun receiptSummary(products: Int, needHelp: Int): String
    val whatIsThis: String
    val isThisRight: String
    val whereDoesItGo: String
    val leftOut: String
    val leftOutBefore: String
    val addMissing: String

    // Settings
    val noShoppingList: String
    fun linkedTo(title: String): String
    val offerShopping: String
    val theme: String
    val language: String
    val systemDefault: String
    val light: String
    val dark: String
}

object EnglishStrings : Strings {
    override val messages = EnglishMessages
    override val back = "Back"
    override val more = "More"
    override val fewer = "Fewer"
    override val cancel = "Cancel"
    override val close = "Close"
    override val ok = "OK"
    override val save = "Save"
    override val delete = "Delete"
    override val rename = "Rename"
    override val add = "Add"
    override val name = "Name"
    override val undo = "Undo"
    override val redo = "Redo"
    override fun undone(action: PantryAction) = "Undone: ${describe(action)}"
    override fun redone(action: PantryAction) = "Redone: ${describe(action)}"
    override fun undone(action: ListAction) = "Undone: ${describe(action)}"
    override fun redone(action: ListAction) = "Redone: ${describe(action)}"

    private fun describe(action: PantryAction) = when (action) {
        is PantryAction.AddedFood -> "added “${action.name}”"
        is PantryAction.UsedOne -> "used one “${action.name}”"
        is PantryAction.Finished -> "finished “${action.name}”"
        is PantryAction.DeletedFood -> "deleted “${action.name}”"
        is PantryAction.EditedFood -> "edited “${action.name}”"
        is PantryAction.MovedFood -> "moved “${action.name}”"
        is PantryAction.PutAway -> if (action.count == 1) "put away 1 item from the receipt" else "put away ${action.count} items from the receipt"
        is PantryAction.AddedSpace -> "added space “${action.name}”"
        is PantryAction.RenamedSpace -> "renamed space “${action.name}”"
        is PantryAction.MovedSpace -> "moved space “${action.name}”"
        is PantryAction.DeletedSpace -> "deleted space “${action.name}”"
    }

    private fun describe(action: ListAction) = when (action) {
        is ListAction.AddedItem -> "added “${action.text}”"
        is ListAction.CheckedItem -> "checked “${action.text}”"
        is ListAction.UncheckedItem -> "unchecked “${action.text}”"
        ListAction.Other -> "the last change"
    }
    override val notNow = "Not now"

    override val pantry = "Pantry"
    override val search = "Search"
    override val searchFood = "Search food"
    override val closeSearch = "Close search"
    override fun noFoodMatches(query: String) = "No food called “$query”."
    override val scanReceipt = "Scan a receipt"
    override val shoppingList = "Shopping list"
    override val settings = "Settings"
    override val addSpace = "Add space"
    override val newSpace = "New space"
    override val sortByExpiry = "Sort by expiry date"
    override val sortByName = "Sort by name"
    override val expandAll = "Expand all"
    override val collapseAll = "Collapse all"
    override val addFood = "Add food"
    override val addHere = "Add food here"
    override val doneAdding = "Done adding"
    override val whereFoodIs = "Where do you keep food?"
    override val whereFoodIsHelp =
        "Add the places you store food in: cupboards, fridges, freezers, the cellar. Tap the ones you have, or name your own."
    override val commonSpaces = listOf("Kitchen cupboard", "Fridge", "Freezer", "Pantry", "Cellar")
    override val otherSpace = "Another space…"
    override val useSoon = "Use soon"
    override val withoutSpace = "Without a space"
    override val spaceOptions = "Space options"
    override val expandSpace = "Expand space"
    override val collapseSpace = "Collapse space"
    override val moveUp = "Move up"
    override val moveDown = "Move down"
    override val deleteSpace = "Delete space"
    override val renameSpace = "Rename space"
    override val spaceNameHint = "Name, e.g. Basement fridge"
    override fun deleteTitle(name: String) = "Delete “$name”?"
    override val deleteSpaceMessage = "The food listed in it is deleted too."
    override fun usedOne(name: String) = "Mark one $name as used"
    override val readingReceipt = "Reading the receipt…"
    override val nothingOnReceipt =
        "No products found on the receipt. Try again with the whole receipt flat and in focus."
    override val scanFailed = "The receipt could not be read."

    override fun addedTo(name: String, space: String) = "$name put in $space"
    override fun movedTo(name: String, space: String) = "$name moved to $space"
    override fun finishedMessage(name: String) = "$name finished"
    override fun left(name: String, count: Int) = "$name: $count left"
    override fun deleted(name: String) = "$name deleted"
    override fun onShoppingList(name: String) = "$name is on the shopping list"
    override fun putAway(count: Int) = if (count == 1) "1 product put away" else "$count products put away"

    override val food = "Food"
    override val where = "Where"
    override fun usuallyIn(space: String) = "Usually in $space"
    override val howMany = "How many"
    override val expires = "Expires"
    override val noDate = "No date"
    override val pickDate = "Pick a date"
    override fun usuallyKeeps(date: String) = "As usual: $date"
    override val editFood = "Food"
    override val finished = "Finished"
    override fun moveTitle(name: String) = "Move $name"
    override val move = "Move"
    override val noOtherSpace = "There is no other space yet. Add one from the menu."
    override val starMeansUsual = "★ is where it usually goes from here."
    override fun allOf(count: Int) = "All $count"

    override val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    override fun expired(daysAgo: Int) = if (daysAgo == 1) "Expired yesterday" else "Expired $daysAgo days ago"
    override fun expiresIn(days: Int) = when (days) {
        0 -> "Expires today"
        1 -> "Expires tomorrow"
        else -> "Expires in $days days"
    }
    override fun expiresOn(date: String) = "Expires $date"

    override fun finishedTitle(name: String) = "$name is finished"
    override val linkToAddFinished =
        "Link a shopping list from Open Check Lists, and finished food can go straight on it."
    override val addToShopping = "Add to the shopping list"
    override val nameOnList = "Name on the list"
    override val alreadyOnListHeading = "Already on the list"
    override fun listItemChoice(text: String, sublist: String?, ticked: Boolean) =
        text + (sublist?.let { " · $it" } ?: "") + if (ticked) " · ticked, put it back" else " · still to buy"
    override val addAsNew = "Add as a new item"
    override fun alreadyOnShoppingList(name: String) = "$name was on the shopping list already"
    override fun putOnShoppingList(name: String) = "Put $name on the shopping list?"
    override fun onList(title: String) = "On “$title”."
    override val sublist = "Sublist"
    override val noSublist = "No sublist"
    override val newSublist = "New sublist…"
    override val sublistName = "Sublist name"
    override val linkShoppingList = "Link a shopping list"
    override val linkIntro =
        "In Open Check Lists, open your grocery list, tap the share icon and share it through Nextcloud. Then copy its link and paste it here. Both apps then work on the same list."
    override val shareLink = "Share link"
    override val sharePassword = "Share password"
    override val link = "Link"
    override val whichList = "Which list?"
    override val severalLists = "This shared folder holds several lists."
    override val noShoppingListTitle = "No shopping list yet"
    override val noShoppingListHelp =
        "Link the grocery list you keep in Open Check Lists. When food is finished here, it can go straight on that list."
    override val linkAnother = "Link another list"
    override val unlink = "Unlink"
    override val unlinkTitle = "Unlink the shopping list?"
    override val unlinkMessage = "The list stays as it is in Open Check Lists; this app just stops using it."
    override val syncNow = "Sync now"
    override val retry = "Retry"
    override val syncing = "Syncing…"
    override val syncedJustNow = "Synced just now"
    override fun syncedMinutesAgo(minutes: Long) = "Synced $minutes min ago"
    override fun syncedHoursAgo(hours: Long) = if (hours == 1L) "Synced an hour ago" else "Synced $hours hours ago"
    override fun syncedDaysAgo(days: Long) = "Synced $days days ago"

    override val sharePantry = "Share the pantry"
    override val useSharedPantry = "Use a pantry shared from another phone"
    override val sharePantryIntro =
        "In Nextcloud, share a folder by link with editing allowed, then copy the link and paste it here. The pantry " +
            "is kept there, with what the app has learned and the shopping list. On another phone, paste the same link."
    override val share = "Share"
    override val whichPantry = "Which pantry?"
    override val severalPantries = "This shared folder holds several pantries."
    override val joinTitle = "This phone has a pantry of its own"
    override val joinMessage = "The shared pantry exists already. What should happen to the spaces and food on this phone?"
    override val joinMerge = "Add them to the shared pantry; spaces with the same name become one"
    override val joinReplace = "Use only the shared pantry; this phone's is kept aside in a file"
    override val sharedPantry = "Shared pantry"
    override val notShared = "This pantry is on this phone only."
    override fun sharedAs(file: String) = "Shared through Nextcloud, as “$file”."
    override val addressForOthers = "To share it on another phone, paste this address there:"
    override val stopSharing = "Stop sharing on this phone"
    override fun sharedPantryProblem(message: String) = "Shared pantry: $message"

    override val receipt = "Receipt"
    override val photoHint =
        "Lay the receipt flat in good light and photograph it whole. If it is long, take several photos from top to " +
            "bottom, each showing the last lines of the one before."
    override val takePhoto = "Take a photo"
    override val takeAnotherPhoto = "Take another"
    override val fromGallery = "From gallery"
    override val readReceipt = "Read the receipt"
    override val readingPhoto = "Reading…"
    override fun linesRead(count: Int) = if (count == 1) "1 line read" else "$count lines read"
    override fun photoNumber(number: Int) = "Photo $number"
    override fun removePhoto(number: Int) = "Remove photo $number"
    override val discardReceipt = "Discard"
    override fun receiptSummary(products: Int, needHelp: Int) =
        (if (products == 1) "1 product." else "$products products.") +
            (if (needHelp > 0) " $needHelp need your help, shown first." else "") +
            " Check them, then save."
    override val whatIsThis = "What is this? The name is only a guess from the receipt."
    override val isThisRight = "Is this right?"
    override val whereDoesItGo = "Where does it go?"
    override val leftOut = "Left out"
    override val leftOutBefore = "Left out, as last time"
    override val addMissing = "Add a missing product"

    override val noShoppingList = "No list linked."
    override fun linkedTo(title: String) = "Linked to “$title”."
    override val offerShopping = "Offer to put finished food on the shopping list"
    override val theme = "Theme"
    override val language = "Language"
    override val systemDefault = "System default"
    override val light = "Light"
    override val dark = "Dark"
}

object ItalianStrings : Strings {
    override val messages = ItalianMessages
    override val back = "Indietro"
    override val more = "Altro"
    override val fewer = "Meno"
    override val cancel = "Annulla"
    override val close = "Chiudi"
    override val ok = "OK"
    override val save = "Salva"
    override val delete = "Elimina"
    override val rename = "Rinomina"
    override val add = "Aggiungi"
    override val name = "Nome"
    override val undo = "Annulla"
    override val redo = "Ripeti"
    override fun undone(action: PantryAction) = "Annullato: ${describe(action)}"
    override fun redone(action: PantryAction) = "Ripetuto: ${describe(action)}"
    override fun undone(action: ListAction) = "Annullato: ${describe(action)}"
    override fun redone(action: ListAction) = "Ripetuto: ${describe(action)}"

    private fun describe(action: PantryAction) = when (action) {
        is PantryAction.AddedFood -> "aggiunta di “${action.name}”"
        is PantryAction.UsedOne -> "consumo di “${action.name}”"
        is PantryAction.Finished -> "esaurimento di “${action.name}”"
        is PantryAction.DeletedFood -> "eliminazione di “${action.name}”"
        is PantryAction.EditedFood -> "modifica di “${action.name}”"
        is PantryAction.MovedFood -> "spostamento di “${action.name}”"
        is PantryAction.PutAway ->
            if (action.count == 1) "sistemazione di 1 prodotto dello scontrino" else "sistemazione di ${action.count} prodotti dello scontrino"
        is PantryAction.AddedSpace -> "aggiunta dello spazio “${action.name}”"
        is PantryAction.RenamedSpace -> "cambio del nome dello spazio “${action.name}”"
        is PantryAction.MovedSpace -> "spostamento dello spazio “${action.name}”"
        is PantryAction.DeletedSpace -> "eliminazione dello spazio “${action.name}”"
    }

    private fun describe(action: ListAction) = when (action) {
        is ListAction.AddedItem -> "aggiunta di “${action.text}”"
        is ListAction.CheckedItem -> "spunta di “${action.text}”"
        is ListAction.UncheckedItem -> "rimozione della spunta di “${action.text}”"
        ListAction.Other -> "ultima modifica"
    }
    override val notNow = "Non ora"

    override val pantry = "Dispensa"
    override val search = "Cerca"
    override val searchFood = "Cerca un alimento"
    override val closeSearch = "Chiudi la ricerca"
    override fun noFoodMatches(query: String) = "Nessun alimento si chiama “$query”."
    override val scanReceipt = "Scansiona uno scontrino"
    override val shoppingList = "Lista della spesa"
    override val settings = "Impostazioni"
    override val addSpace = "Aggiungi spazio"
    override val newSpace = "Nuovo spazio"
    override val sortByExpiry = "Ordina per scadenza"
    override val sortByName = "Ordina per nome"
    override val expandAll = "Espandi tutto"
    override val collapseAll = "Comprimi tutto"
    override val addFood = "Aggiungi alimento"
    override val addHere = "Aggiungi qui"
    override val doneAdding = "Fine"
    override val whereFoodIs = "Dove tieni il cibo?"
    override val whereFoodIsHelp =
        "Aggiungi i posti dove conservi il cibo: mobili, frigoriferi, congelatori, la cantina. Tocca quelli che hai, o dai un nome tuo."
    override val commonSpaces = listOf("Mobile della cucina", "Frigorifero", "Congelatore", "Dispensa", "Cantina")
    override val otherSpace = "Un altro spazio…"
    override val useSoon = "Da consumare presto"
    override val withoutSpace = "Senza spazio"
    override val spaceOptions = "Opzioni dello spazio"
    override val expandSpace = "Espandi lo spazio"
    override val collapseSpace = "Comprimi lo spazio"
    override val moveUp = "Sposta su"
    override val moveDown = "Sposta giù"
    override val deleteSpace = "Elimina spazio"
    override val renameSpace = "Rinomina spazio"
    override val spaceNameHint = "Nome, es. Frigo della taverna"
    override fun deleteTitle(name: String) = "Eliminare “$name”?"
    override val deleteSpaceMessage = "Vengono eliminati anche gli alimenti che contiene."
    override fun usedOne(name: String) = "Segna come consumato: $name"
    override val readingReceipt = "Lettura dello scontrino…"
    override val nothingOnReceipt =
        "Nessun prodotto trovato sullo scontrino. Riprova con lo scontrino intero, ben steso e a fuoco."
    override val scanFailed = "Non è stato possibile leggere lo scontrino."

    override fun addedTo(name: String, space: String) = "Aggiunto in $space: $name"
    override fun movedTo(name: String, space: String) = "Spostato in $space: $name"
    override fun finishedMessage(name: String) = "Finito: $name"
    override fun left(name: String, count: Int) = "$name: ne restano $count"
    override fun deleted(name: String) = "Eliminato: $name"
    override fun onShoppingList(name: String) = "$name è nella lista della spesa"
    override fun putAway(count: Int) = if (count == 1) "1 prodotto messo via" else "$count prodotti messi via"

    override val food = "Alimento"
    override val where = "Dove"
    override fun usuallyIn(space: String) = "Di solito in $space"
    override val howMany = "Quanti"
    override val expires = "Scadenza"
    override val noDate = "Nessuna"
    override val pickDate = "Scegli una data"
    override fun usuallyKeeps(date: String) = "Come al solito: $date"
    override val editFood = "Alimento"
    override val finished = "Finito"
    override fun moveTitle(name: String) = "Sposta $name"
    override val move = "Sposta"
    override val noOtherSpace = "Non c'è ancora un altro spazio. Aggiungilo dal menu."
    override val starMeansUsual = "★ è dove va di solito da qui."
    override fun allOf(count: Int) = "Tutti e $count"

    override val months = listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic")
    override fun expired(daysAgo: Int) = if (daysAgo == 1) "Scaduto ieri" else "Scaduto da $daysAgo giorni"
    override fun expiresIn(days: Int) = when (days) {
        0 -> "Scade oggi"
        1 -> "Scade domani"
        else -> "Scade tra $days giorni"
    }
    override fun expiresOn(date: String) = "Scade il $date"

    override fun finishedTitle(name: String) = "Finito: $name"
    override val linkToAddFinished =
        "Collega una lista della spesa di Open Check Lists, e ciò che finisce può andarci direttamente."
    override val addToShopping = "Aggiungi alla lista della spesa"
    override val nameOnList = "Nome nella lista"
    override val alreadyOnListHeading = "Già nella lista"
    override fun listItemChoice(text: String, sublist: String?, ticked: Boolean) =
        text + (sublist?.let { " · $it" } ?: "") + if (ticked) " · spuntato, rimettilo" else " · da comprare"
    override val addAsNew = "Aggiungi come nuova voce"
    override fun alreadyOnShoppingList(name: String) = "$name era già nella lista della spesa"
    override fun putOnShoppingList(name: String) = "Mettere $name nella lista della spesa?"
    override fun onList(title: String) = "In “$title”."
    override val sublist = "Sottolista"
    override val noSublist = "Nessuna sottolista"
    override val newSublist = "Nuova sottolista…"
    override val sublistName = "Nome della sottolista"
    override val linkShoppingList = "Collega una lista della spesa"
    override val linkIntro =
        "In Open Check Lists apri la lista della spesa, tocca l'icona di condivisione e condividila tramite Nextcloud. Poi copia il link e incollalo qui. Le due app lavoreranno sulla stessa lista."
    override val shareLink = "Link di condivisione"
    override val sharePassword = "Password della condivisione"
    override val link = "Collega"
    override val whichList = "Quale lista?"
    override val severalLists = "Questa cartella condivisa contiene più liste."
    override val noShoppingListTitle = "Ancora nessuna lista della spesa"
    override val noShoppingListHelp =
        "Collega la lista della spesa che tieni in Open Check Lists. Quando qui qualcosa finisce, può andare direttamente in quella lista."
    override val linkAnother = "Collega un'altra lista"
    override val unlink = "Scollega"
    override val unlinkTitle = "Scollegare la lista della spesa?"
    override val unlinkMessage = "La lista resta com'è in Open Check Lists; questa app smette solo di usarla."
    override val syncNow = "Sincronizza ora"
    override val retry = "Riprova"
    override val syncing = "Sincronizzazione…"
    override val syncedJustNow = "Sincronizzata ora"
    override fun syncedMinutesAgo(minutes: Long) = "Sincronizzata $minutes min fa"
    override fun syncedHoursAgo(hours: Long) = if (hours == 1L) "Sincronizzata un'ora fa" else "Sincronizzata $hours ore fa"
    override fun syncedDaysAgo(days: Long) = "Sincronizzata $days giorni fa"

    override val sharePantry = "Condividi la dispensa"
    override val useSharedPantry = "Usa una dispensa condivisa da un altro telefono"
    override val sharePantryIntro =
        "In Nextcloud condividi una cartella tramite link, permettendo la modifica, poi copia il link e incollalo qui. " +
            "La dispensa viene tenuta lì, con ciò che l'app ha imparato e la lista della spesa. Su un altro telefono incolla lo stesso link."
    override val share = "Condividi"
    override val whichPantry = "Quale dispensa?"
    override val severalPantries = "Questa cartella condivisa contiene più dispense."
    override val joinTitle = "Questo telefono ha già una sua dispensa"
    override val joinMessage = "La dispensa condivisa esiste già. Cosa fare degli spazi e degli alimenti di questo telefono?"
    override val joinMerge = "Aggiungerli alla dispensa condivisa; gli spazi con lo stesso nome diventano uno"
    override val joinReplace = "Usare solo la dispensa condivisa; quella di questo telefono viene conservata a parte in un file"
    override val sharedPantry = "Dispensa condivisa"
    override val notShared = "Questa dispensa è solo su questo telefono."
    override fun sharedAs(file: String) = "Condivisa tramite Nextcloud, come “$file”."
    override val addressForOthers = "Per condividerla su un altro telefono, incolla lì questo indirizzo:"
    override val stopSharing = "Smetti di condividere su questo telefono"
    override fun sharedPantryProblem(message: String) = "Dispensa condivisa: $message"

    override val receipt = "Scontrino"
    override val photoHint =
        "Stendi lo scontrino in un posto ben illuminato e fotografalo intero. Se è lungo, scatta più foto dall'inizio " +
            "alla fine: ognuna deve riprendere le ultime righe della precedente."
    override val takePhoto = "Scatta una foto"
    override val takeAnotherPhoto = "Scattane un'altra"
    override val fromGallery = "Dalla galleria"
    override val readReceipt = "Leggi lo scontrino"
    override val readingPhoto = "Lettura…"
    override fun linesRead(count: Int) = if (count == 1) "1 riga letta" else "$count righe lette"
    override fun photoNumber(number: Int) = "Foto $number"
    override fun removePhoto(number: Int) = "Togli la foto $number"
    override val discardReceipt = "Scarta"
    override fun receiptSummary(products: Int, needHelp: Int) =
        (if (products == 1) "1 prodotto." else "$products prodotti.") +
            (if (needHelp > 0) " $needHelp hanno bisogno di te, sono in cima." else "") +
            " Controllali, poi salva."
    override val whatIsThis = "Che cos'è? Il nome è solo ricavato dallo scontrino."
    override val isThisRight = "È giusto?"
    override val whereDoesItGo = "Dove va?"
    override val leftOut = "Escluso"
    override val leftOutBefore = "Escluso, come l'ultima volta"
    override val addMissing = "Aggiungi un prodotto mancante"

    override val noShoppingList = "Nessuna lista collegata."
    override fun linkedTo(title: String) = "Collegata a “$title”."
    override val offerShopping = "Proponi di mettere nella lista della spesa ciò che finisce"
    override val theme = "Tema"
    override val language = "Lingua"
    override val systemDefault = "Predefinito di sistema"
    override val light = "Chiaro"
    override val dark = "Scuro"
}

val LocalStrings = staticCompositionLocalOf<Strings> { EnglishStrings }

val strings: Strings
    @Composable @ReadOnlyComposable get() = LocalStrings.current
