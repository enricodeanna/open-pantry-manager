package eu.studiodeanna.openpantrymanager

/**
 * User-facing texts produced below the UI: sync errors and the like. The UI picks the language by
 * setting [current]; a message is worded when it is raised, so errors already shown keep theirs.
 */
interface Messages {
    val syncFailed: String
    val chooseList: String
    val noListsInFolder: String
    val keptChanging: String
    val notAShareLink: String
    val driveNotYet: String
    val notAList: String
    val newerVersion: String

    fun cantReach(server: String): String
    val shareNeedsPassword: String
    val shareNotFound: String
    val shareReadOnly: String
    val cannotCreate: String
    fun nextcloudRefusedSave(detail: String?): String
    val nextcloudRefusedAccess: String
    fun nextcloudAnswered(status: String): String
    fun withNextcloudDetail(message: String, detail: String): String

    val noCameraApp: String
    val photoUnreadable: String

    companion object {
        var current: Messages = EnglishMessages
    }
}

object EnglishMessages : Messages {
    override val syncFailed = "Sync failed."
    override val chooseList = "Choose which list to use."
    override val noListsInFolder = "There are no lists in this shared folder yet."
    override val keptChanging = "The shopping list kept changing while syncing. It will retry."
    override val notAShareLink = "Not a share link this app can use. Paste the Nextcloud link of a list from Open Check Lists."
    override val driveNotYet = "Lists kept in Google Drive can't be used yet. Share the list through Nextcloud instead."
    override val notAList = "This file is not a list from Open Check Lists."
    override val newerVersion =
        "This list was saved by a newer version of Open Check Lists. Update Open Pantry Manager to use it."

    override fun cantReach(server: String) = "Can't reach $server. Check the connection."
    override val shareNeedsPassword = "The share needs a password, or the password is wrong."
    override val shareNotFound = "Share not found. The link may have expired or been unshared."
    override val shareReadOnly = "This link is read-only. In Nextcloud, turn on “Allow editing” for the share."
    override val cannotCreate =
        "This link can edit files but not add new ones, so the list file can't be created in the folder. " +
            "In Nextcloud, set the folder link to allow upload and editing."
    override fun nextcloudRefusedSave(detail: String?) =
        "Nextcloud refused to save the list" + (detail?.let { ": $it" } ?: ".")
    override val nextcloudRefusedAccess = "Nextcloud refused access to this share."
    override fun nextcloudAnswered(status: String) = "Nextcloud answered $status."
    override fun withNextcloudDetail(message: String, detail: String) = "$message (Nextcloud: $detail)"

    override val noCameraApp = "There is no camera app to take the photo with."
    override val photoUnreadable = "The text on the photo could not be read."
}

object ItalianMessages : Messages {
    override val syncFailed = "Sincronizzazione non riuscita."
    override val chooseList = "Scegli quale lista usare."
    override val noListsInFolder = "Questa cartella condivisa non contiene ancora liste."
    override val keptChanging = "La lista della spesa continuava a cambiare durante la sincronizzazione. Verrà ritentata."
    override val notAShareLink =
        "Questo non è un link di condivisione utilizzabile. Incolla il link Nextcloud di una lista di Open Check Lists."
    override val driveNotYet =
        "Le liste tenute in Google Drive non si possono ancora usare. Condividi la lista tramite Nextcloud."
    override val notAList = "Questo file non è una lista di Open Check Lists."
    override val newerVersion =
        "Questa lista è stata salvata da una versione più recente di Open Check Lists. Aggiorna Open Pantry Manager per usarla."

    override fun cantReach(server: String) = "Impossibile raggiungere $server. Controlla la connessione."
    override val shareNeedsPassword = "La condivisione richiede una password, oppure la password è sbagliata."
    override val shareNotFound = "Condivisione non trovata. Il link potrebbe essere scaduto o revocato."
    override val shareReadOnly = "Questo link è di sola lettura. In Nextcloud, attiva “Consenti modifica” per la condivisione."
    override val cannotCreate =
        "Questo link può modificare i file ma non aggiungerne di nuovi, quindi il file della lista non può essere creato " +
            "nella cartella. In Nextcloud, imposta il link della cartella per consentire caricamento e modifica."
    override fun nextcloudRefusedSave(detail: String?) =
        "Nextcloud ha rifiutato di salvare la lista" + (detail?.let { ": $it" } ?: ".")
    override val nextcloudRefusedAccess = "Nextcloud ha negato l'accesso a questa condivisione."
    override fun nextcloudAnswered(status: String) = "Nextcloud ha risposto $status."
    override fun withNextcloudDetail(message: String, detail: String) = "$message (Nextcloud: $detail)"

    override val noCameraApp = "Non c'è un'app fotocamera con cui scattare la foto."
    override val photoUnreadable = "Non è stato possibile leggere il testo della foto."
}
