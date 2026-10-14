package eu.studiodeanna.openpantrymanager

import eu.studiodeanna.openpantrymanager.model.liveItems
import eu.studiodeanna.openpantrymanager.store.FileStore
import eu.studiodeanna.openpantrymanager.sync.ShareLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Two phones sharing a pantry through a real Nextcloud server: runs only when
 * `OPM_NEXTCLOUD_TEST_LINK` holds the link of a shared folder that allows editing, and leaves a
 * "Pantry test.pantry" file there. Real time, not the test scheduler's: HTTP timeouts must not fire at once.
 */
class RealNextcloudTest {
    private class MemoryStore : FileStore {
        val files = mutableMapOf<String, String>()
        override suspend fun read(name: String) = files[name]
        override suspend fun write(name: String, content: String) {
            files[name] = content
        }
        override suspend fun delete(name: String) {
            files.remove(name)
        }
    }

    @Test
    fun twoPhonesShareAPantryThroughNextcloud() {
        val url = System.getenv("OPM_NEXTCLOUD_TEST_LINK") ?: return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            runBlocking {
                val a = PantryRepository(MemoryStore(), scope).also { it.load() }
                val fridge = a.addSpace("Fridge")
                a.addItem("Latte", fridge, 2)
                a.sharePantry(ShareLink(url), "Pantry test")
                assertEquals("Pantry test.pantry", a.sharing.value?.link?.fileName)

                val b = PantryRepository(MemoryStore(), scope).also { it.load() }
                b.sharePantry(ShareLink(url), "Pantry test")
                assertEquals(listOf("Latte"), b.pantry.value.liveItems().map { it.name })
                assertEquals(fridge, b.placeFor("latte"))

                b.addItem("Pasta", fridge)
                b.syncPantry()
                a.syncPantry()
                assertEquals(listOf("Latte", "Pasta"), a.pantry.value.liveItems().map { it.name }.sorted())
                assertEquals(fridge, a.placeFor("pasta"))
                assertEquals(SyncState.Synced::class, a.sharing.value?.sync?.let { it::class })
            }
        } finally {
            scope.cancel()
        }
    }
}
