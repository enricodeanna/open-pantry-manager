package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import eu.studiodeanna.openpantrymanager.Messages
import eu.studiodeanna.openpantrymanager.PantryRepository
import eu.studiodeanna.openpantrymanager.checklist.Item
import eu.studiodeanna.openpantrymanager.ReceiptReview
import kotlinx.coroutines.launch

/**
 * The whole app. [scanner] reads receipts, [voice] hears voice commands and [reminders] shows daily
 * reminders where the platform can; without them, these are not offered. [shortcut] is where the app was
 * just opened to go, from outside it; [onShortcut] says it went there.
 */
@Composable
fun PantryApp(
    repository: PantryRepository,
    scanner: ReceiptScanner? = null,
    voice: VoiceInput? = null,
    reminders: Reminders? = null,
    shortcut: Shortcut? = null,
    onShortcut: () -> Unit = {},
) {
    val settings by repository.settings.collectAsState()
    val strings = stringsFor(settings.language)
    SideEffect { Messages.current = strings.messages }
    PantryTheme(darkTheme = settings.theme.isDark()) {
        CompositionLocalProvider(LocalStrings provides strings) {
            Surface(Modifier.fillMaxSize()) {
                var loaded by remember { mutableStateOf(false) }
                LaunchedEffect(repository) {
                    repository.load()
                    loaded = true
                    repository.syncNow()
                }
                if (!loaded) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    return@Surface
                }

                val nav = rememberNavController()
                val scope = rememberCoroutineScope()
                // Shared by the screens, so a message from one (receipt saved) shows on the next.
                val snackbars = remember { SnackbarHostState() }
                var receipt by remember { mutableStateOf<ReceiptReview?>(null) }
                /** The shopping list's items under review, when [receipt] was made of them rather than read on a receipt. */
                var fromList by remember { mutableStateOf<List<Item>?>(null) }
                // Opened from a shortcut: back to the pantry, which searches or listens; or on to the camera.
                LaunchedEffect(shortcut) {
                    if (shortcut == null) return@LaunchedEffect
                    nav.popBackStack("pantry", inclusive = false)
                    if (shortcut == Shortcut.Receipt) {
                        if (scanner != null) nav.navigate("photos")
                        onShortcut()
                    }
                }
                NavHost(nav, startDestination = "pantry") {
                    composable("pantry") {
                        PantryScreen(
                            repository,
                            snackbars,
                            onShopping = { nav.navigate("shopping") },
                            onCosts = { nav.navigate("costs") },
                            onScan = scanner?.let { { nav.navigate("photos") } },
                            voice = voice,
                            reminders = reminders,
                            onPutAway = { items ->
                                fromList = items
                                receipt = ReceiptReview(repository.entriesFor(items))
                                nav.navigate("receipt")
                            },
                            shortcut = shortcut?.takeIf { it != Shortcut.Receipt },
                            onShortcut = onShortcut,
                        )
                    }
                    composable("photos") {
                        if (scanner == null) return@composable
                        ReceiptPhotosScreen(
                            repository,
                            scanner,
                            snackbars,
                            onBack = { nav.popBackStack() },
                            onRead = {
                                receipt = it
                                fromList = null
                                // Back from the review goes to the pantry: the photos are done with.
                                nav.navigate("receipt") { popUpTo("photos") { inclusive = true } }
                            },
                        )
                    }
                    composable("costs") {
                        CostsScreen(repository, onBack = { nav.popBackStack() })
                    }
                    composable("shopping") {
                        ShoppingScreen(repository, onBack = { nav.popBackStack() })
                    }
                    composable("receipt") {
                        val review = receipt
                        if (review == null) {
                            // After the app was closed in the middle of a review: nothing left to review.
                            LaunchedEffect(Unit) { nav.popBackStack() }
                            return@composable
                        }
                        val items = fromList
                        ReceiptScreen(
                            repository,
                            review,
                            // The entries stay until the next scan replaces them: clearing them here would
                            // make this screen leave a second time, taking the pantry with it.
                            onBack = { nav.popBackStack() },
                            title = if (items != null) strings.putAwayTitle else strings.receipt,
                            closeLabel = if (items != null) strings.close else strings.discardReceipt,
                            save = if (items != null) {
                                { e, bought, shop -> repository.putAwayFromList(e, items, bought, shop) }
                            } else {
                                { e, bought, shop -> repository.saveReceipt(e, bought, shop, review.shopRow) }
                            },
                            onSaved = { count, step ->
                                nav.popBackStack()
                                scope.launch {
                                    snackbars.currentSnackbarData?.dismiss()
                                    val result = snackbars.showSnackbar(strings.putAway(count), strings.undo, duration = SnackbarDuration.Long)
                                    if (result == SnackbarResult.ActionPerformed && step != null) repository.undo(step)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
