package eu.studiodeanna.openpantrymanager

import eu.studiodeanna.openpantrymanager.memory.Ingredient
import eu.studiodeanna.openpantrymanager.memory.Memory
import eu.studiodeanna.openpantrymanager.memory.Recipe
import eu.studiodeanna.openpantrymanager.memory.groupOf
import eu.studiodeanna.openpantrymanager.model.PantryDocument
import eu.studiodeanna.openpantrymanager.model.PantryItem
import eu.studiodeanna.openpantrymanager.model.liveItems
import eu.studiodeanna.openpantrymanager.model.nameKey
import kotlinx.datetime.LocalDate

/**
 * What of one item of the pantry goes into a recipe being made: [quantity] pieces or boxes; for boxes,
 * whether they are used up ([whole]) or opened with some left, and then how much of a box of known
 * size is used ([amount]), if said.
 */
data class RecipeUse(val item: String, val quantity: Int = 1, val whole: Boolean = true, val amount: Double? = null)

/**
 * What of a dish just made goes in the pantry: [portions] of it in [space], until [expires] if known, as
 * [leftover]s, or the whole dish made ahead (a meal for the freezer, say).
 */
data class Dish(val space: String, val portions: Int = 1, val expires: LocalDate? = null, val leftover: Boolean)

/** What a recipe made before takes from the pantry now ([uses]), and what of it the pantry lacks ([missing]). */
data class Suggestion(val uses: List<RecipeUse>, val missing: List<Ingredient>)

/**
 * What [recipe] would take from [pantry], as it went last time: for each ingredient, the items of that
 * food, or of a like one (its group of products, [groupOf]: "Cottage cheese" for "Fiocchi di latte"),
 * opened boxes first, then the one expiring soonest, as much as the pantry has. Leftovers are meals,
 * not ingredients.
 */
fun suggestFor(recipe: Recipe, pantry: PantryDocument, memory: Memory): Suggestion {
    val uses = mutableListOf<RecipeUse>()
    val missing = mutableListOf<Ingredient>()
    val taken = HashMap<String, Int>()
    val order = compareBy<PantryItem>({ it.opened == null }, { it.expires == null }, { it.expires })
    for (ingredient in recipe.ingredients) {
        val candidates = pantry.liveItems().filter { !it.leftover }
        val same = candidates.filter { nameKey(it.name) == nameKey(ingredient.food) }
            .ifEmpty { candidates.filter { nameKey(memory.groupOf(it.name)) == nameKey(memory.groupOf(ingredient.food)) } }
            .sortedWith(order)
        var wanted = ingredient.quantity.coerceAtLeast(1)
        for (item in same) {
            if (wanted <= 0) break
            val free = item.quantity - (taken[item.id] ?: 0)
            if (free <= 0) continue
            // Opened, used in part: one box at a time.
            val count = if (item.boxed && !ingredient.whole) 1 else minOf(wanted, free)
            taken[item.id] = (taken[item.id] ?: 0) + count
            uses += RecipeUse(item.id, count, if (item.boxed) ingredient.whole else true, ingredient.amount.takeIf { item.size != null && !ingredient.whole })
            wanted -= count
        }
        if (wanted == ingredient.quantity.coerceAtLeast(1)) missing += ingredient
    }
    return Suggestion(uses, missing)
}

/**
 * The ingredients a recipe was made with, from [uses] of the items they took: the same food once, its
 * pieces or boxes added up.
 */
fun ingredientsOf(uses: List<Pair<PantryItem, RecipeUse>>): List<Ingredient> =
    uses.map { (item, use) ->
        val whole = !item.boxed || use.whole
        Ingredient(item.name, use.quantity.coerceAtLeast(1), whole, use.amount.takeIf { !whole && item.size != null })
    }.groupBy { nameKey(it.food) }.values.map { same ->
        same.first().copy(quantity = same.sumOf { it.quantity }, amount = same.mapNotNull { it.amount }.takeIf { it.isNotEmpty() }?.sum())
    }
