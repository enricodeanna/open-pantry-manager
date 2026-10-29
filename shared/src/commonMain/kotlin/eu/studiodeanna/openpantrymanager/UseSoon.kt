package eu.studiodeanna.openpantrymanager

import eu.studiodeanna.openpantrymanager.memory.Memory
import eu.studiodeanna.openpantrymanager.memory.food
import eu.studiodeanna.openpantrymanager.memory.usualOpenDays
import eu.studiodeanna.openpantrymanager.model.PantryDocument
import eu.studiodeanna.openpantrymanager.model.PantryItem
import eu.studiodeanna.openpantrymanager.model.liveItems
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** Food is pointed out when it is to be used within this many days, or should have been already. */
const val SOON_DAYS = 3

/**
 * The day an opened box of [item] is usually used up by: the day it was opened, and as many days as
 * that food usually lasted opened; null for food not opened, or not finished from an opened box twice.
 */
fun Memory.usedUpBy(item: PantryItem): LocalDate? {
    val opened = item.opened ?: return null
    val days = food(item.name)?.usualOpenDays() ?: return null
    return opened.plus(DatePeriod(days = days))
}

/** The day to use [item] by: its expiry day, or the day an opened box of it usually lasts to, whichever comes first. */
fun Memory.useBy(item: PantryItem): LocalDate? = listOfNotNull(item.expires, usedUpBy(item)).minOrNull()

/** Food to use within [SOON_DAYS] days of [today], or past its day, with that day: the soonest first. */
fun PantryDocument.useSoon(memory: Memory, today: LocalDate): List<Pair<PantryItem, LocalDate>> =
    liveItems()
        .mapNotNull { item -> memory.useBy(item)?.takeIf { today.daysUntil(it) <= SOON_DAYS }?.let { item to it } }
        .sortedBy { it.second }
