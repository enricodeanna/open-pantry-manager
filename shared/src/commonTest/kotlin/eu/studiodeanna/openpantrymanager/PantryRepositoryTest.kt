package eu.studiodeanna.openpantrymanager

import eu.studiodeanna.openpantrymanager.checklist.ListAction
import eu.studiodeanna.openpantrymanager.checklist.ListCodec
import eu.studiodeanna.openpantrymanager.checklist.itemsBySection
import eu.studiodeanna.openpantrymanager.checklist.visibleSections
import eu.studiodeanna.openpantrymanager.costs.CostGrouping
import eu.studiodeanna.openpantrymanager.costs.Measure
import eu.studiodeanna.openpantrymanager.costs.MeasureUnit
import eu.studiodeanna.openpantrymanager.costs.costLines
import eu.studiodeanna.openpantrymanager.costs.livePurchases
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class PantryRepositoryTest {
    private val files = MemoryFileStore()
    private var clock = 1_000_000L

    private fun TestScope.repository(http: io.ktor.client.HttpClient = FakeNextcloud().http) =
        PantryRepository(files, this, http, now = { clock++ }, date = { LocalDate(2026, 10, 14) }, work = StandardTestDispatcher(testScheduler))

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
        ).entries
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

    @Test
    fun aTapOpensABoxTheNextFinishesItAndTheLastBoxOpenedAsksToBuyMore() = runTest {
        val repo = repository()
        repo.load()
        val cabinet = repo.addSpace("Cabinet")
        val date = LocalDate(2026, 12, 12)
        repo.addItem("Cookies", cabinet, 2, date, boxed = true)
        val cookies = repo.pantry.value.liveItems().single()

        // Two boxes: one opened, listed apart; one closed is left, so there is no need to buy more.
        val opened = repo.useOne(cookies.id)!!
        assertEquals(PantryAction.Opened("Cookies"), opened.action)
        assertEquals(false, opened.ranOut)
        val open = repo.pantry.value.liveItems().single { it.opened != null }
        assertEquals(Triple(1, LocalDate(2026, 10, 14), date), Triple(open.quantity, open.opened, open.expires))
        assertEquals(1, repo.pantry.value.item(cookies.id)?.quantity)

        // Undone, the two are one again.
        repo.undo()
        assertEquals(listOf(2 to null), repo.pantry.value.liveItems().map { it.quantity to it.opened })
        repo.redo()

        // The next tap on the opened box finishes it, without asking: a closed one is left.
        val finished = repo.useOne(open.id)!!
        assertEquals(PantryAction.Finished("Cookies") to false, finished.action to finished.ranOut)
        // The last closed box opened: time to buy more.
        assertEquals(true, repo.useOne(cookies.id)!!.ranOut)
        // Finishing it, then, asks no more.
        assertEquals(PantryAction.Finished("Cookies") to false, repo.useOne(cookies.id)!!.let { it.action to it.ranOut })
        assertEquals(emptyList(), repo.pantry.value.liveItems())
    }

    @Test
    fun foodLeftUnderOtherDatesIsNotOfferedForTheShoppingList() = runTest {
        val repo = repository()
        repo.load()
        val fridge = repo.addSpace("Fridge")
        repo.addItem("Milk", fridge, 1, LocalDate(2026, 10, 20))
        repo.addItem("milk", fridge, 1)
        val (first, second) = repo.pantry.value.liveItems().sortedBy { it.expires == null }
        assertEquals(false, repo.useOne(first.id)!!.ranOut)
        assertEquals(true, repo.finish(second.id)!!.ranOut)

        // Deleted, not finished: nothing to buy.
        repo.addItem("Bread", fridge)
        repo.removeItem(repo.pantry.value.liveItems().single().id)
    }

    @Test
    fun learnsWhichFoodComesInBoxes() = runTest {
        val repo = repository()
        repo.load()
        val cabinet = repo.addSpace("Cabinet")
        repo.addItem("Cereals", cabinet, boxed = true)
        // Added again without saying, as from the quick field or a receipt, it comes in a box.
        repo.addItem("cereals", cabinet, 1, LocalDate(2027, 1, 1))
        assertEquals(listOf(true, true), repo.pantry.value.liveItems().map { it.boxed })
        assertEquals(true, repo.food("Cereals")?.boxed)

        // Food opened by voice that was not in boxes comes in boxes from now on.
        repo.addItem("Rice", cabinet)
        val rice = repo.pantry.value.liveItems().single { it.name == "Rice" }
        assertEquals(true, repo.open(rice.id)!!.ranOut)
        assertEquals(true, repo.food("rice")?.boxed)

        // Marked opened in its dialog, the last closed box asks to buy more too; a rename alone does not.
        val cereals = repo.pantry.value.liveItems().first { it.name == "Cereals" }
        assertEquals(false, repo.editItem(cereals.id, "Muesli", 1, null, cabinet)!!.ranOut)
        val muesli = repo.pantry.value.item(cereals.id)!!
        assertEquals(true, repo.editItem(muesli.id, "Muesli", 1, null, cabinet, opened = LocalDate(2026, 10, 14))!!.ranOut)
        // Not in boxes after all: it is not opened either, and the app learns that.
        repo.editItem(muesli.id, "Muesli", 1, null, cabinet, boxed = false)
        assertEquals(false to null, repo.pantry.value.item(muesli.id)!!.let { it.boxed to it.opened })
        assertEquals(false, repo.food("Muesli")?.boxed)
    }

    @Test
    fun whatWasTickedOnTheListIsPutAwayOnceAndUndoneTogether() = runTest {
        val cloud = FakeNextcloud(folder = true)
        cloud.overwrite(openCheckListsFile, "Groceries.json")
        val repo = repository(cloud.http)
        repo.load()
        repo.linkShoppingList(cloud.link)
        val fridge = repo.addSpace("Fridge")
        repo.addItem("Milk, semi-skimmed", fridge, 1, LocalDate(2026, 10, 21))

        // Milk was ticked: let go, it is not offered again until ticked anew.
        assertEquals(listOf("item00000001"), repo.boughtOnList().map { it.id })
        repo.letGoFromList(repo.boughtOnList())
        assertEquals(emptyList(), repo.boughtOnList())
        repo.useShoppingItem("Milk, semi-skimmed", "item00000001")
        repo.setShoppingDone("item00000001", true)
        repo.setShoppingDone("item00000002", true)
        val bought = repo.boughtOnList()
        assertEquals(listOf("Milk", "Bread"), bought.map { it.text })

        // Milk is the food it went on the list for, with its place and its usual keeping time; Bread is new.
        val entries = repo.entriesFor(bought)
        assertEquals(
            listOf(Triple("Milk, semi-skimmed", fridge, LocalDate(2026, 10, 21)), Triple("Bread", null, null)),
            entries.map { Triple(it.name, it.space, it.expires) },
        )
        assertEquals("Milk", entries.first().line.text)
        val (step, count) = repo.putAwayFromList(entries.map { if (it.space == null) it.copy(space = fridge) else it }, bought)
        assertEquals(2, count)
        assertEquals(mapOf("Milk, semi-skimmed" to 2, "Bread" to 1), repo.pantry.value.liveItems().associate { it.name to it.quantity })
        assertEquals(emptyList(), repo.boughtOnList())

        // Undone, the food leaves the pantry and the items are offered again.
        assertEquals(PantryAction.PutAwayFromList(2), repo.undo(step!!))
        assertEquals(listOf("Milk", "Bread"), repo.boughtOnList().map { it.text })
        assertEquals(1, repo.pantry.value.liveItems().single().quantity)
    }

    @Test
    fun learnsHowLongAnOpenedBoxLastsAndPointsItOutBeforeThen() = runTest {
        var day = LocalDate(2026, 10, 1)
        val repo = PantryRepository(files, this, FakeNextcloud().http, now = { clock++ }, date = { day }, work = StandardTestDispatcher(testScheduler))
        repo.load()
        val fridge = repo.addSpace("Fridge")
        repeat(2) {
            repo.addItem("Milk", fridge, boxed = true)
            val milk = repo.pantry.value.liveItems().single()
            repo.useOne(milk.id)
            day = day.plus(DatePeriod(days = 4))
            repo.useOne(milk.id)
        }
        assertEquals(listOf(4, 4), repo.food("Milk")?.openLasts)

        repo.addItem("Milk", fridge, boxed = true)
        val milk = repo.pantry.value.liveItems().single()
        repo.useOne(milk.id)
        val opened = repo.pantry.value.item(milk.id)!!
        val usedUp = day.plus(DatePeriod(days = 4))
        assertEquals(usedUp, repo.memory.value.usedUpBy(opened))
        // Four days away is not soon yet; the day after, it is, though it has no expiry day.
        assertEquals(emptyList(), repo.pantry.value.useSoon(repo.memory.value, day))
        assertEquals(listOf(opened to usedUp), repo.pantry.value.useSoon(repo.memory.value, day.plus(DatePeriod(days = 1))))
        // Its expiry day counts when it comes first.
        repo.editItem(milk.id, "Milk", 1, day.plus(DatePeriod(days = 2)), fridge)
        assertEquals(day.plus(DatePeriod(days = 2)), repo.memory.value.useBy(repo.pantry.value.item(milk.id)!!))
    }

    @Test
    fun anOpenedBoxIsUsedByTheAmountTakenAndTheUsualServingIsLearned() = runTest {
        val repo = repository()
        repo.load()
        val fridge = repo.addSpace("Fridge")
        val pot = Measure(500.0, MeasureUnit.Gram)
        repo.addItem("Yogurt", fridge, 1, boxed = true, size = pot)
        val yogurt = repo.pantry.value.liveItems().single()
        assertEquals(pot, yogurt.size)

        val opened = repo.useOne(yogurt.id)!!
        assertEquals(PantryAction.Opened("Yogurt"), opened.action)
        assertEquals(yogurt.id to pot, opened.item to opened.leftAmount)
        val used = repo.useAmount(yogurt.id, 100.0)!!
        assertEquals(PantryAction.UsedAmount("Yogurt", Measure(100.0, MeasureUnit.Gram)), used.action)
        assertEquals(Measure(400.0, MeasureUnit.Gram), used.leftAmount)
        repo.useAmount(yogurt.id, 150.0)
        assertEquals(listOf(100.0, 150.0), repo.food("Yogurt")?.servings)
        assertEquals(250.0, repo.pantry.value.item(yogurt.id)?.left)

        // The rest of it: finished, as a tap on its last one would.
        assertEquals(PantryAction.Finished("Yogurt"), repo.useAmount(yogurt.id, 250.0)!!.action)
        assertEquals(emptyList(), repo.pantry.value.liveItems())
        repo.undo()
        assertEquals(250.0, repo.pantry.value.item(yogurt.id)?.left)

        // Known now, the size comes by itself next time.
        repo.addItem("yogurt", fridge, 2, LocalDate(2026, 11, 1))
        assertEquals(pot, repo.pantry.value.liveItems().single { it.expires != null }.size)
    }

    @Test
    fun pricesGoInTheCostsAddedUpByProductGroupSublistAndShop() = runTest {
        val repo = repository()
        repo.load()
        val fridge = repo.addSpace("Fridge")
        val pot = Measure(500.0, MeasureUnit.Gram)
        repo.addItem("Yogurt intero", fridge, 2, boxed = true, size = pot, price = 398, shop = "Coop")
        val first = repo.costs.value.livePurchases().single()
        assertEquals(Triple(2, 398L, Measure(1000.0, MeasureUnit.Gram)), Triple(first.quantity, first.cents, first.amount))
        assertEquals("Coop" to LocalDate(2026, 10, 14), first.shop to first.day)

        // A receipt's prices come with it, less discounts, and its shop is learned from its header.
        val review = repo.understandReceipt(
            listOf(
                listOf(
                    OcrLine("COOP ALLEANZA 3.0", 10f, 0f, 400f, 30f),
                    OcrLine("VIA ROMA 12", 10f, 50f, 300f, 80f),
                    OcrLine("YOGURT INTERO 500G", 10f, 100f, 400f, 130f),
                    OcrLine("1,99", 600f, 100f, 680f, 130f),
                    OcrLine("SCONTO -0,50", 10f, 150f, 680f, 180f),
                    OcrLine("TOTALE 1,49", 10f, 200f, 680f, 230f),
                ),
            ),
        )
        assertEquals(1.49 to "Coop Alleanza", review.total to review.shop)
        val entry = review.entries.single()
        assertEquals(1.49 to pot, entry.price to entry.size)
        val (step, _) = repo.saveReceipt(listOf(entry.copy(name = "Yogurt intero", space = fridge)), shop = "Coop", shopRow = review.shopRow)
        assertEquals(2, repo.costs.value.livePurchases().size)
        assertEquals("Coop", repo.understandReceipt(listOf(listOf(OcrLine("COOP ALLEANZA 3.0", 10f, 0f, 400f, 30f)))).shop)
        // Undone, the purchase goes with the food.
        repo.undo(step!!)
        assertEquals(1, repo.costs.value.livePurchases().size)
        repo.redo()

        // Like products, grouped once, count together; sublists and shops as known.
        repo.addItem("Fiocchi di latte", fridge, price = 150, shop = "Lidl")
        repo.addItem("Cottage cheese", fridge, price = 210, shop = "Lidl")
        repo.setGroup("Cottage cheese", "Fiocchi di latte")
        repo.setSublist("Yogurt intero", "Latticini")
        repo.setSublist("Fiocchi di latte", "Latticini")
        val costs = repo.costs.value
        val memory = repo.memory.value
        assertEquals(
            listOf("Yogurt intero" to 547L, "Fiocchi di latte" to 360L),
            costLines(costs, memory, CostGrouping.Group).map { it.name to it.cents },
        )
        assertEquals(listOf("Latticini" to 697L, "" to 210L), costLines(costs, memory, CostGrouping.Sublist).map { it.name to it.cents })
        assertEquals(listOf("Coop" to 547L, "Lidl" to 360L), costLines(costs, memory, CostGrouping.Shop).map { it.name to it.cents })
        val yogurt = costLines(costs, memory, CostGrouping.Product).first()
        // 3 pots for 5,47 €: 1,82 € each, 3,65 € a kilogram; the last, 1,49 €, is 25% below the 1,99 € before.
        assertEquals(Triple(182L, 365L, MeasureUnit.Gram), Triple(yogurt.centsPerPiece, yogurt.centsPerThousand, yogurt.unit))
        assertEquals(-0.25, yogurt.lastChange!!, 0.01)

        // A purchase can be put right, or deleted; either can be undone.
        val lidl = repo.costs.value.livePurchases().first { it.food == "Cottage cheese" }
        repo.editPurchase(lidl.id, 1, 199, lidl.day, "Lidl", null)
        assertEquals(199L, repo.costs.value.livePurchases().first { it.id == lidl.id }.cents)
        repo.deletePurchase(lidl.id)
        assertEquals(3, repo.costs.value.livePurchases().size)
        assertEquals(PantryAction.DeletedPurchase("Cottage cheese"), repo.undo())
        assertEquals(4, repo.costs.value.livePurchases().size)
    }
}
