package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import eu.studiodeanna.openpantrymanager.SOON_DAYS
import eu.studiodeanna.openpantrymanager.model.PantryItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** "21 Oct", with the year when it is not [today]'s. */
fun Strings.shortDate(date: LocalDate, today: LocalDate): String {
    val day = "${date.day} ${months[date.month.ordinal]}"
    return if (date.year == today.year) day else "$day ${date.year}"
}

/** When food expires, said relative to [today] when that is near. */
fun Strings.expiry(date: LocalDate, today: LocalDate): String {
    val days = today.daysUntil(date)
    return when {
        days < 0 -> expired(-days)
        days <= 7 -> expiresIn(days)
        else -> expiresOn(shortDate(date, today))
    }
}

/**
 * Why food is to be used by [useBy] (see [eu.studiodeanna.openpantrymanager.useBy]): its expiry day, or,
 * for an opened box that will not last that long, since when it is open.
 */
fun Strings.useByText(item: PantryItem, useBy: LocalDate, today: LocalDate): String {
    val opened = item.opened
    return if (opened != null && useBy != item.expires) openedAgo(opened.daysUntil(today)) else expiry(useBy, today)
}

/** The colour an expiry is shown in: the error colour once past, the warning colour when soon. */
@Composable
fun expiryColor(date: LocalDate, today: LocalDate): Color {
    val days = today.daysUntil(date)
    return when {
        days < 0 -> MaterialTheme.colorScheme.error
        days <= SOON_DAYS -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** The date pickers work in UTC milliseconds at midnight. */
fun LocalDate.toPickerMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

fun pickerMillisToDate(millis: Long): LocalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
