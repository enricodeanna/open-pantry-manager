package eu.studiodeanna.openpantrymanager

import eu.studiodeanna.openpantrymanager.checklist.ListAction
import eu.studiodeanna.openpantrymanager.checklist.ListCodec
import eu.studiodeanna.openpantrymanager.checklist.itemsBySection
import eu.studiodeanna.openpantrymanager.checklist.visibleSections
import eu.studiodeanna.openpantrymanager.model.PantryAction
import eu.studiodeanna.openpantrymanager.model.UndoState
import eu.studiodeanna.openpantrymanager.model.item
import eu.studiodeanna.openpantrymanager.model.liveItems
import eu.studiodeanna.openpantrymanager.model.visibleSpaces
import eu.studiodeanna.openpantrymanager.receipt.OcrLine
import eu.studiodeanna.openpantrymanager.receipt.Recognition
import eu.studiodeanna.openpantrymanager.store.MemoryFileStore
import eu.studiodeanna.openpantrymanager.sync.FakeNextcloud
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class PantryRepositoryTest {
    private val files = MemoryFileStore()
    private var clock = 1_000_000L

    private fun TestScope.repository(http: io.ktor.client.HttpClient = FakeNextcloud().http) =
        PantryRepository(files, this, http, now = { clock++ }, date = { LocalDate(2026, 10, 14) })

    /** A list as Open Check Lists writes it: a title, two sections, items in and outside them. */
    private val openCheckListsFile = """
        {
            "format": 1,
            "title": "Groceries",
            "titleAt": 10,
            "titleBy": "a1b2c3d4",
            "sections": [
                { "id": "dairy0000001", "name": "Dairy", "pos": 1.0, "at": 11, "by": "a1b2c3d4", "deleted": false },
                { "id": "greens000001", "name": "Greengrocer", "pos": 2.0, "at": 12, "by": "a1b2c3d4", "deleted": false }
            ],
            "items": [
                { "id": "item00000001", "text": "Milk", "section": "dairy0000001", "done": true, "pos": 1.0, "at": 13, "by": "a1b2c3d4", "deleted": false },
                { "id": "item00000002", "text": "Bread", "section": null, "done": false, "pos": 1.0, "at": 14, "by": "a1b2c3d4", "deleted": false }
            ]
        }
    """.trimIndent()

    @Test
    fun learnsWhereFoodGoesAndWhereItMoves() = runTest {
        val repo = repository()
        repo.load()
        val cabinet = repo.addSpace("Cabinet")
        val basement = repo.addSpace("Basement")
        repo.addItem("Water", basement, 6)
        assertEquals(basement, repo.placeFor("water"))
        val water = repo.pantry.value.liveItems().single()
        repo.moveItem(water.id, cabinet, 1)
        assertEquals(cabinet, repo.moveFor(repo.pantry.value.liveItems().first { it.space == basement }))
        advanceUntilIdle()

        // What was learned survives a restart.
        val again = repository()
        again.load()
        assertEquals(basement, again.placeFor("Water"))
        assertEquals(mapOf(basement to 5, cabinet to 1), again.pantry.value.liveItems().associate { it.space to it.quantity })
    }

    @Test
    fun undoTakesBackTheChangeAndWhatWasLearnedFromIt() = runTest {
        val repo = repository()
        repo.load()
        val cabinet = repo.addSpace("Cabinet")
        val fridge = repo.addSpace("Fridge")
        repo.addItem("Milk", fridge)
        val milk = repo.pantry.value.liveItems().single()
        val step = repo.moveItem(milk.id, cabinet)!!
        assertEquals(PantryAction.MovedFood("Milk"), repo.undo(step))
        assertEquals(fridge, repo.pantry.value.liveItems().single().space)
        assertNull(repo.moveFor(repo.pantry.value.liveItems().single()))
        // A message's Undo acts only while its edit is the latest.
        assertNull(repo.undo(step))
        assertEquals(1, repo.pantry.value.liveItems().size)
    }

    @Test
    fun editsAreUndoneAndRedoneOneByOneAsInOpenCheckLists() = runTest {
        val repo = repository()
        repo.load()
        val fridge = repo.addSpace("Fridge")
        repo.addItem("Milk", fridge, 2)
        val milk = repo.pantry.value.liveItems().single().id
        repo.useOne(milk)
        repo.useOne(milk)
        assertEquals(emptyList(), repo.pantry.value.liveItems())

        assertEquals(PantryAction.Finished("Milk"), repo.undo())
        assertEquals(PantryAction.UsedOne("Milk"), repo.undo())
        assertEquals(2, repo.pantry.value.item(milk)?.quantity)
        assertEquals(UndoState(canUndo = true, canRedo = true), repo.undoState.value)
        assertEquals(PantryAction.UsedOne("Milk"), repo.redo())
        assertEquals(1, repo.pantry.value.item(milk)?.quantity)

        // Undone back to the start, the space and what was learned go too; redone, they come back.
        repo.undo()
        repo.undo()
        assertEquals(PantryAction.AddedSpace("Fridge"), repo.undo())
        assertEquals(emptyList(), repo.pantry.value.visibleSpaces())
        assertNull(repo.food("milk"))
        assertNull(repo.undo())
        repo.redo()
        repo.redo()
        assertEquals(fridge, repo.placeFor("milk"))
        assertEquals(2, repo.pantry.value.item(milk)?.quantity)

        // A new edit can be undone, and what was undone before it can no longer be redone.
        repo.addItem("Eggs", fridge)
        assertEquals(UndoState(canUndo = true, canRedo = false), repo.undoState.value)
        assertNull(repo.redo())
    }

    @Test
    fun tickingTheShoppingListIsUndoneAndRedone() = runTest {
        val cloud = FakeNextcloud(folder = true)
        cloud.overwrite(openCheckListsFile, "Groceries.json")
        val repo = repository(cloud.http)
        repo.load()
        repo.linkShoppingList(cloud.link)
        repo.setShoppingDone("item00000002", true)
        repo.addToShopping("Apples", Sublist.New("Fruit"))
        assertEquals(ListAction.AddedItem("Apples"), repo.undoShopping())
        assertEquals(listOf("Dairy", "Greengrocer"), repo.shopping.value!!.doc.visibleSections().map { it.name })
        assertEquals(ListAction.CheckedItem("Bread"), repo.undoShopping())
        assertEquals(false, repo.shopping.value!!.doc.items.single { it.id == "item00000002" }.done)
        assertEquals(ListAction.CheckedItem("Bread"), repo.redoShopping())
        assertEquals(true, repo.shopping.value!!.doc.items.single { it.id == "item00000002" }.done)
        // Undoing the list leaves the pantry's history alone.
        assertEquals(UndoState(), repo.undoState.value)
    }

    @Test
    fun aReceiptIsUnderstoodBetterEachTime() = runTest {
        val repo = repository()
        repo.load()
        val fridge = repo.addSpace("Fridge")
        fun receipt() = repo.understandReceipt(
            listOf(
                listOf(
                    OcrLine("LATTE P.S. ESSELUNGA 1L", 10f, 100f, 400f, 130f),
                    OcrLine("1,15", 600f, 100f, 680f, 130f),
                    OcrLine("SHOPPER BIO", 10f, 150f, 300f, 180f),
                    OcrLine("0,10", 600f, 150f, 680f, 180f),
                    OcrLine("TOTALE 1,25", 10f, 200f, 680f, 230f),
                ),
            ),
        )
        val first = receipt()
        assertEquals(listOf(Recognition.Unknown, Recognition.Unknown), first.map { it.recognition })
        // The line is a pack of 6 bottles.
        val (_, added) = repo.saveReceipt(
            listOf(
                first[0].copy(name = "Milk", space = fridge, quantity = 6, expires = LocalDate(2026, 10, 21)),
                first[1].copy(include = false),
            ),
        )
        assertEquals(1, added)

        val second = receipt()
        assertEquals(listOf(Recognition.Known, Recognition.Skipped), second.map { it.recognition })
        assertEquals(listOf("Milk", fridge, 6, LocalDate(2026, 10, 21)), second[0].let { listOf(it.name, it.space, it.quantity, it.expires) })
        assertEquals(false, second[1].include)
    }

    @Test
    fun putsFinishedFoodOnAnOpenCheckListsListInTheSameFormat() = runTest {
        val cloud = FakeNextcloud(folder = true)
        cloud.overwrite(openCheckListsFile, "Groceries.json")
        val repo = repository(cloud.http)
        repo.load()
        repo.linkShoppingList(cloud.link)
        val list = repo.shopping.value!!
        assertEquals("Groceries.json", list.link.fileName)
        assertEquals(listOf("Dairy", "Greengrocer"), list.doc.visibleSections().map { it.name })

        // Milk is ticked in Dairy: it is unticked, not added twice. Apples go in a new sublist.
        repo.addToShopping("Milk", Sublist.Existing("dairy0000001"))
        repo.addToShopping("Apples", Sublist.New("Fruit"))
        repo.sync()

        val remote = ListCodec.decode(cloud.file("Groceries.json")!!)
        val bySection = remote.itemsBySection()
        val fruit = remote.visibleSections().single { it.name == "Fruit" }
        assertEquals(listOf("Milk" to false), bySection["dairy0000001"]?.map { it.text to it.done })
        assertEquals(listOf("Apples"), bySection[fruit.id]?.map { it.text })
        assertEquals(listOf("Bread"), bySection[null]?.map { it.text })
        // Field names and values are written as Open Check Lists writes them.
        val text = cloud.file("Groceries.json")!!
        assertTrue("\"format\": 1" in text && "\"titleBy\": \"a1b2c3d4\"" in text && "\"done\": false" in text, text)

        // Next time, Milk is suggested for Dairy.
        assertEquals(Sublist.Existing("dairy0000001"), repo.sublistFor("milk"))
    }

    @Test
    fun aFolderWithSeveralListsAsksWhichOne() = runTest {
        val cloud = FakeNextcloud(folder = true)
        cloud.overwrite(openCheckListsFile, "Groceries.json")
        cloud.overwrite(openCheckListsFile.replace("Groceries", "Hardware"), "Hardware.json")
        val repo = repository(cloud.http)
        repo.load()
        val choice = assertFailsWith<ListChoiceNeeded> { repo.linkShoppingList(cloud.link) }
        assertEquals(listOf("Groceries", "Hardware"), choice.lists.map { it.title })
        repo.linkShoppingList(choice.link.copy(fileName = "Hardware.json"))
        assertEquals("Hardware", repo.shopping.value?.doc?.title)
    }

    @Test
    fun findsWhatIsOnTheListAlreadyAndRemembersWhatFoodIsCalledThere() = runTest {
        val cloud = FakeNextcloud(folder = true)
        cloud.overwrite(openCheckListsFile, "Groceries.json")
        val repo = repository(cloud.http)
        repo.load()
        repo.linkShoppingList(cloud.link)

        // Milk is ticked in Dairy: picked for "Milk, semi-skimmed", it goes back on the list, and is its name there.
        val milk = repo.similarOnList("Milk, semi-skimmed").single()
        assertEquals("Milk" to true, milk.text to milk.done)
        repo.useShoppingItem("Milk, semi-skimmed", milk.id)
        assertEquals(false, repo.shopping.value!!.doc.items.single { it.id == milk.id }.done)
        assertEquals("Milk", repo.shoppingNameFor("milk semi skimmed"))
        assertEquals(Sublist.Existing("dairy0000001"), repo.sublistFor("Milk, semi-skimmed"))

        // A name typed for the list is remembered too; the food's own name is not stored twice.
        repo.addToShopping("Pane comune", "Bread", Sublist.None)
        assertEquals("Bread", repo.shoppingNameFor("Pane comune"))
        repo.addToShopping("Apples", Sublist.None)
        assertEquals("Apples", repo.shoppingNameFor("apples"))
        assertNull(repo.food("apples")!!.shoppingName)
        // Bread was pending already: not added twice.
        assertEquals(1, repo.shopping.value!!.doc.items.count { it.text == "Bread" && !it.deleted })
    }
}
