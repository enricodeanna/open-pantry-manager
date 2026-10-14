package eu.studiodeanna.openpantrymanager.android

import android.app.Application
import eu.studiodeanna.openpantrymanager.PantryRepository
import eu.studiodeanna.openpantrymanager.defaultHttpClient
import eu.studiodeanna.openpantrymanager.store.DirectoryFileStore
import kotlinx.coroutines.MainScope
import java.io.File

class PantryApplication : Application() {
    /** Outlives activities, so a rotation neither reloads the pantry nor interrupts a sync. */
    val repository by lazy {
        PantryRepository(DirectoryFileStore(File(filesDir, "pantry")), MainScope(), defaultHttpClient())
    }
}
