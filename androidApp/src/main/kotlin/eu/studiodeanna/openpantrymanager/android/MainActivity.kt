package eu.studiodeanna.openpantrymanager.android

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.studiodeanna.openpantrymanager.ui.PantryApp
import eu.studiodeanna.openpantrymanager.ui.Shortcut
import eu.studiodeanna.openpantrymanager.ui.Strings
import eu.studiodeanna.openpantrymanager.ui.isDark
import eu.studiodeanna.openpantrymanager.ui.stringsFor
import eu.studiodeanna.openpantrymanager.voice.VoiceLanguages
import java.io.File

class MainActivity : ComponentActivity() {
    private val app get() = application as PantryApplication

    // Made before the activity starts, as Android requires of what waits for other apps' results.
    private val scanner = PhotoReceiptScanner(this)
    private val voice = VoskVoiceInput(this)
    private val reminders = AndroidReminders(this)

    /** Where a shortcut of the launcher icon asked to go, until the app has gone there. */
    private var shortcut by mutableStateOf<Shortcut?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Test builds can be driven over adb: handed receipt photos to "take" instead of the camera's and a
        // recorded sentence to "hear" instead of the microphone's, and shown over the lock screen of a test phone.
        // adb shell am start -n eu.studiodeanna.openpantrymanager/.android.MainActivity --es receiptImage <path>[,<path>…] \
        //     --es voiceAudio <16 kHz mono WAV> --ez overLockScreen true
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            intent.getStringExtra("receiptImage")?.let { paths -> scanner.nextImages = ArrayDeque(paths.split(',').map(::File)) }
            intent.getStringExtra("voiceAudio")?.let { voice.nextAudio = File(it) }
            if (intent.getBooleanExtra("overLockScreen", false) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
        }
        // Not again when the activity is made anew, after Android stopped it in the background.
        if (savedInstanceState == null) shortcut = shortcutOf(intent)
        setContent {
            // The bars' icons follow the app's theme, which the settings may set apart from the system's.
            val settings by app.repository.settings.collectAsState()
            LaunchedEffect(settings.expiryReminders) { ExpiryReminders.schedule(this@MainActivity, settings.expiryReminders) }
            LaunchedEffect(settings.language) { showShortcuts(stringsFor(settings.language)) }
            val dark = settings.theme.isDark()
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
            }
            PantryApp(app.repository, scanner, voice, reminders, shortcut) {
                shortcut = null
                intent.removeExtra(SHORTCUT)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shortcutOf(intent)?.let { shortcut = it }
    }

    override fun onStop() {
        super.onStop()
        // Out of sight: the speech model's memory is better given back now.
        voice.release()
    }

    private fun shortcutOf(intent: Intent?): Shortcut? =
        intent?.getStringExtra(SHORTCUT)?.let { name -> Shortcut.entries.firstOrNull { it.name == name } }

    /**
     * The shortcuts a long press on the launcher icon shows, in the app's language: search, voice
     * commands (where the language has them), and a receipt's photos.
     */
    private fun showShortcuts(strings: Strings) {
        val manager = getSystemService(ShortcutManager::class.java) ?: return
        fun shortcut(to: Shortcut, short: String, long: String, icon: Int) = ShortcutInfo.Builder(this, to.name)
            .setShortLabel(short)
            .setLongLabel(long)
            .setIcon(Icon.createWithResource(this, icon))
            .setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(SHORTCUT, to.name))
            .build()
        val shortcuts = buildList {
            add(shortcut(Shortcut.Search, strings.search, strings.searchFood, R.drawable.ic_shortcut_search))
            if (VoiceLanguages.forCode(strings.languageCode) != null) {
                add(shortcut(Shortcut.Voice, strings.voiceShort, strings.voiceCommand, R.drawable.ic_shortcut_voice))
            }
            add(shortcut(Shortcut.Receipt, strings.receipt, strings.scanReceipt, R.drawable.ic_shortcut_receipt))
        }
        // Android limits how often they change; the old ones stay meanwhile.
        runCatching { manager.dynamicShortcuts = shortcuts }
    }

    override fun onResume() {
        super.onResume()
        // Pick up what was ticked or added in Open Check Lists meanwhile, and the shared pantry's changes.
        app.repository.syncNow()
    }

    private companion object {
        /** The extra of a shortcut's intent naming where it goes. */
        const val SHORTCUT = "shortcut"
        // The scrims enableEdgeToEdge uses by default.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
