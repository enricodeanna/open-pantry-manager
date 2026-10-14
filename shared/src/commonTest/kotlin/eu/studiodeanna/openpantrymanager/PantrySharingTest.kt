package eu.studiodeanna.openpantrymanager

import eu.studiodeanna.openpantrymanager.checklist.ListCodec
import eu.studiodeanna.openpantrymanager.model.PantryCodec
import eu.studiodeanna.openpantrymanager.model.liveItems
import eu.studiodeanna.openpantrymanager.model.visibleSpaces
import eu.studiodeanna.openpantrymanager.store.MemoryFileStore
import eu.studiodeanna.openpantrymanager.sync.FakeNextcloud
import eu.studiodeanna.openpantrymanager.sync.RemoteException
import eu.studiodeanna.openpantrymanager.sync.SharedPantry
import eu.studiodeanna.openpantrymanager.sync.SharedPantryCodec
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Two phones sharing a pantry through a Nextcloud folder. Each sync is run here when it is due: the
 * fake server answers on another thread, which the test scheduler does not wait for.
 */
class PantrySharingTest {
    private var clock = 1_000_000L
    private val cloud = FakeNextcloud(folder = true)

    private suspend fun TestScope.phone(files: MemoryFileStore = MemoryFileStore(), http: io.ktor.client.HttpClient = cloud.http) =
        PantryRepository(files, this, http, now = { clock++ }, date = { LocalDate(2026, 10, 14) }).also { it.load() }

    private fun PantryRepository.names() = pantry.value.liveItems().map { it.name }.sorted()

    private fun PantryRepository.space(name: String) = pantry.value.visibleSpaces().single { it.name == name }.id

    /** An Open Check Lists list, as that app writes it. */
    private val groceries = """{"format": 1, "title": "Groceries", "titleAt": 10, "titleBy": "a1b2c3d4", "sections": [], "items": []}"""

    @Test
    fun aPantrySharedIntoAFolderIsJoinedByAnotherPhoneWithAllItLearned() = runTest {
        val a = phone()
        val fridge = a.addSpace("Fridge")
        a.addItem("Latte", fridge, 2)
        a.sharePantry(cloud.link, "Dispensa")
        assertEquals(setOf("Dispensa.pantry"), cloud.fileNames)
        assertEquals("Dispensa.pantry", a.sharing.value?.link?.fileName)

        val b = phone()
        b.sharePantry(cloud.link, "Dispensa")
        assertEquals(listOf("Latte"), b.names())
        assertEquals(fridge, b.placeFor("latte"))

        // From now on each phone's changes reach the other.
        b.addItem("Pasta", fridge)
        b.syncPantry()
        a.syncPantry()
        assertEquals(listOf("Latte", "Pasta"), a.names())
        assertEquals(fridge, a.placeFor("pasta"))
        a.useOne(a.pantry.value.liveItems().single { it.name == "Latte" }.id)
        a.removeItem(a.pantry.value.liveItems().single { it.name == "Pasta" }.id)
        a.syncPantry()
        b.syncPantry()
        assertEquals(listOf("Latte"), b.names())
        assertEquals(1, b.pantry.value.liveItems().single().quantity)
    }

    @Test
    fun aPhoneWithAPantryOfItsOwnIsAskedAndItsSpacesJoinThoseOfTheSameName() = runTest {
        val a = phone()
        val fridge = a.addSpace("Fridge")
        a.addItem("Latte", fridge)
        a.sharePantry(cloud.link, "Dispensa")

        val b = phone()
        val myFridge = b.addSpace("fridge")
        val cellar = b.addSpace("Cellar")
        b.addItem("Burro", myFridge)
        b.addItem("Vino", cellar)
        val choice = assertFailsWith<JoinChoiceNeeded> { b.sharePantry(cloud.link, "Dispensa") }
        b.sharePantry(choice.link, "Dispensa", JoinMode.Merge)

        assertEquals(listOf("Fridge", "Cellar"), b.pantry.value.visibleSpaces().map { it.name })
        assertEquals(listOf("Burro", "Latte"), b.pantry.value.liveItems().filter { it.space == fridge }.map { it.name }.sorted())
        assertEquals(fridge, b.placeFor("burro"))
        a.syncPantry()
        assertEquals(listOf("Burro", "Latte", "Vino"), a.names())
        assertEquals(cellar, a.placeFor("vino"))
    }

    @Test
    fun replacedByTheSharedPantryAPhoneKeepsItsOwnAside() = runTest {
        val a = phone()
        a.addItem("Latte", a.addSpace("Fridge"))
        a.sharePantry(cloud.link, "Dispensa")

        val files = MemoryFileStore()
        val b = phone(files)
        b.addItem("Burro", b.addSpace("Frigo"))
        b.sharePantry(cloud.link, "Dispensa", JoinMode.Replace)
        assertEquals(listOf("Latte"), b.names())
        val aside = files.files.keys.single { it.startsWith("pantry-before-sharing-") }
        assertEquals(listOf("Burro"), PantryCodec.decode(files.files.getValue(aside)).liveItems().map { it.name })
    }

    @Test
    fun theShoppingListGoesWithThePantry() = runTest {
        cloud.overwrite(groceries, "Groceries.json")
        val a = phone()
        a.linkShoppingList(cloud.link)
        a.sharePantry(cloud.link, "Dispensa")

        val b = phone()
        b.sharePantry(cloud.link, "Dispensa")
        assertEquals("Groceries.json", b.shopping.value?.link?.fileName)
        b.sync()
        assertEquals("Groceries", b.shopping.value?.doc?.title)

        // Unlinked on one phone, it is unlinked on the other.
        a.unlinkShoppingList()
        a.syncPantry()
        b.syncPantry()
        assertNull(b.shopping.value)
    }

    @Test
    fun undoTakesBackOnlyItsOwnChangeNotWhatAnotherPhoneDidMeanwhile() = runTest {
        val a = phone()
        val fridge = a.addSpace("Fridge")
        a.sharePantry(cloud.link, "Dispensa")
        val b = phone()
        b.sharePantry(cloud.link, "Dispensa")

        val step = a.addItem("Latte", fridge)!!
        b.addItem("Pasta", fridge)
        b.syncPantry()
        a.syncPantry()
        a.undo(step)
        a.syncPantry()
        b.syncPantry()
        assertEquals(listOf("Pasta"), b.names())
        assertEquals(fridge, b.placeFor("pasta"))
        assertNull(b.food("latte"))
    }

    @Test
    fun aFolderWithSeveralPantriesAsksWhichOne() = runTest {
        phone().also { it.addSpace("Fridge"); it.sharePantry(cloud.link, "Casa") }
        cloud.overwrite(SharedPantryCodec.encode(SharedPantry()), "Mare.pantry")
        val b = phone()
        val choice = assertFailsWith<PantryChoiceNeeded> { b.sharePantry(cloud.link, "Dispensa") }
        assertEquals(listOf("Casa", "Mare"), choice.pantries.map { it.title })
        b.sharePantry(choice.link.copy(fileName = "Mare.pantry"), "Dispensa")
        assertEquals("Mare.pantry", b.sharing.value?.link?.fileName)
    }

    @Test
    fun aListIsNeverTakenForAPantryNorAPantryForAList() = runTest {
        // Next to a list in a folder, the pantry gets a file of its own.
        cloud.overwrite(groceries, "Groceries.json")
        val a = phone()
        a.addSpace("Fridge")
        a.sharePantry(cloud.link, "Dispensa")
        assertEquals(groceries, cloud.file("Groceries.json"))

        // A link to the list itself is refused as a pantry, and the list left alone.
        val listShare = FakeNextcloud(token = "List123").apply { overwrite(groceries) }
        val e = assertFailsWith<RemoteException> { phone(http = listShare.http).sharePantry(listShare.link, "Dispensa") }
        assertEquals(Messages.current.notAPantry, e.message)
        assertEquals(groceries, listShare.content)

        // A link to a pantry is refused as a shopping list, even an empty pantry that reads like an empty list.
        val pantryShare = FakeNextcloud(token = "Pantry123").apply { overwrite(SharedPantryCodec.encode(SharedPantry())) }
        val before = pantryShare.content
        assertFailsWith<RemoteException> { phone(http = pantryShare.http).linkShoppingList(pantryShare.link) }
        assertEquals(before, pantryShare.content)
        assertTrue(runCatching { ListCodec.decode(before!!) }.isSuccess, "an empty pantry does parse as a list")
    }
}
