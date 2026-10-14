package eu.studiodeanna.openpantrymanager.android

import android.content.pm.ApplicationInfo
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
import eu.studiodeanna.openpantrymanager.ui.PantryApp
import eu.studiodeanna.openpantrymanager.ui.isDark
import java.io.File

class MainActivity : ComponentActivity() {
    private val app get() = application as PantryApplication

    // Made before the activity starts, as Android requires of what waits for other apps' results.
    private val scanner = PhotoReceiptScanner(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Test builds can be driven over adb: handed receipt photos to "take" instead of the camera's, and
        // shown over the lock screen of a test phone.
        // adb shell am start -n eu.studiodeanna.openpantrymanager/.android.MainActivity --es receiptImage <path>[,<path>…] --ez overLockScreen true
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            intent.getStringExtra("receiptImage")?.let { paths -> scanner.nextImages = ArrayDeque(paths.split(',').map(::File)) }
            if (intent.getBooleanExtra("overLockScreen", false) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
        }
        setContent {
            // The bars' icons follow the app's theme, which the settings may set apart from the system's.
            val settings by app.repository.settings.collectAsState()
            val dark = settings.theme.isDark()
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
            }
            PantryApp(app.repository, scanner)
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up what was ticked or added in Open Check Lists meanwhile.
        app.repository.syncShopping()
    }

    private companion object {
        // The scrims enableEdgeToEdge uses by default.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
