package eu.studiodeanna.openpantrymanager.android

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import eu.studiodeanna.openpantrymanager.PantryRepository
import eu.studiodeanna.openpantrymanager.ui.Reminders
import eu.studiodeanna.openpantrymanager.ui.stringsFor
import eu.studiodeanna.openpantrymanager.ui.useByText
import eu.studiodeanna.openpantrymanager.useSoon
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.datetime.daysUntil
import java.time.ZonedDateTime

/**
 * Daily reminders of the food to use soon. An alarm in the morning wakes [ExpiryReminderReceiver], which
 * reads the pantry on the phone and, when some food is to be used within a few days, or should have been
 * since yesterday, posts one notification listing it; then it sets the alarm for the next morning.
 * Nothing goes over the network.
 */
object ExpiryReminders {
    private const val CHANNEL = "use-soon"
    private const val NOTIFICATION = 1
    /** The reminder comes between this hour of the morning and [WINDOW] later, as Android groups alarms to save battery. */
    private const val HOUR = 9
    private const val WINDOW = 60 * 60 * 1000L
    /** Lines the notification lists before it only counts the rest. */
    private const val LINES = 6

    /**
     * Sets the alarm for the next morning, or cancels it unless [enabled]. Called whenever the app starts,
     * after each reminder, and after the phone restarts, which drops alarms; setting it again only
     * replaces it. A repeating alarm would be late by up to most of a day.
     */
    fun schedule(context: Context, enabled: Boolean) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val alarm = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ExpiryReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (!enabled) {
            alarms.cancel(alarm)
            return
        }
        val now = ZonedDateTime.now()
        val morning = now.withHour(HOUR).withMinute(0).withSecond(0).withNano(0).let { if (it.isAfter(now)) it else it.plusDays(1) }
        alarms.setWindow(AlarmManager.RTC_WAKEUP, morning.toInstant().toEpochMilli(), WINDOW, alarm)
    }

    /** Whether the system lets the app post notifications. */
    fun allowed(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true
    }

    /**
     * Posts the reminder of the food to use soon in [repository]'s pantry, if the settings want it and
     * there is some; else takes yesterday's away. Food long past its day is not repeated every morning:
     * the pantry shows it.
     */
    fun remind(context: Context, repository: PantryRepository) {
        val settings = repository.settings.value
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!settings.expiryReminders || !allowed(context)) return
        val today = repository.today()
        val soon = repository.pantry.value.useSoon(repository.memory.value, today).filter { (_, day) -> today.daysUntil(day) >= -1 }
        if (soon.isEmpty()) {
            manager.cancel(NOTIFICATION)
            return
        }
        val strings = stringsFor(settings.language)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, strings.useSoon, NotificationManager.IMPORTANCE_DEFAULT))
        val lines = soon.map { (item, day) -> "${item.name} · ${strings.useByText(item, day, today)}" }
        val style = Notification.InboxStyle()
        lines.take(LINES).forEach(style::addLine)
        if (lines.size > LINES) style.setSummaryText("+${lines.size - LINES}")
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(strings.useSoonCount(soon.size))
            .setContentText(soon.joinToString(", ") { it.first.name })
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION, notification)
    }
}

/** Wakes each morning to remind of food to use soon, and sets the next morning's alarm; after a restart or an update, sets it again. */
class ExpiryReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repository = (context.applicationContext as PantryApplication).repository
        val done = goAsync()
        MainScope().launch {
            try {
                repository.load()
                val action = intent.action
                if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
                    ExpiryReminders.remind(context, repository)
                }
                ExpiryReminders.schedule(context, repository.settings.value.expiryReminders)
            } finally {
                done.finish()
            }
        }
    }
}

/** Asks for leave to post notifications where Android wants that (13 and later); made before the activity starts. */
class AndroidReminders(private val activity: ComponentActivity) : Reminders {
    private var answer: CompletableDeferred<Boolean>? = null
    private val ask = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { answer?.complete(it) }

    override fun allowed(): Boolean = ExpiryReminders.allowed(activity)

    override suspend fun allow(): Boolean {
        if (allowed()) return true
        // Before Android 13 there is nothing to ask: notifications were turned off in the system's settings.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val deferred = CompletableDeferred<Boolean>().also { answer = it }
        ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        return deferred.await() && allowed()
    }
}
