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
import androidx.compose.ui.text.intl.Locale
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import eu.studiodeanna.openpantrymanager.Messages
import eu.studiodeanna.openpantrymanager.PantryRepository
import eu.studiodeanna.openpantrymanager.receipt.ReceiptEntry
import eu.studiodeanna.openpantrymanager.store.LanguageChoice
import kotlinx.coroutines.launch

/** The whole app. [scanner] reads receipts where the platform can; without one, scanning is not offered. */
@Composable
fun PantryApp(repository: PantryRepository, scanner: ReceiptScanner? = null) {
    val settings by repository.settings.collectAsState()
    val strings = when (settings.language) {
        LanguageChoice.English -> EnglishStrings
        LanguageChoice.Italian -> ItalianStrings
        LanguageChoice.System -> if (Locale.current.language == "it") ItalianStrings else EnglishStrings
    }
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
                var receipt by remember { mutableStateOf<List<ReceiptEntry>?>(null) }
                NavHost(nav, startDestination = "pantry") {
                    composable("pantry") {
                        PantryScreen(
                            repository,
                            snackbars,
                            onShopping = { nav.navigate("shopping") },
                            onScan = scanner?.let { { nav.navigate("photos") } },
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
                                // Back from the review goes to the pantry: the photos are done with.
                                nav.navigate("receipt") { popUpTo("photos") { inclusive = true } }
                            },
                        )
                    }
                    composable("shopping") {
                        ShoppingScreen(repository, onBack = { nav.popBackStack() })
                    }
                    composable("receipt") {
                        val entries = receipt
                        if (entries == null) {
                            // After the app was closed in the middle of a review: nothing left to review.
                            LaunchedEffect(Unit) { nav.popBackStack() }
                            return@composable
                        }
                        ReceiptScreen(
                            repository,
                            entries,
                            // The entries stay until the next scan replaces them: clearing them here would
                            // make this screen leave a second time, taking the pantry with it.
                            onBack = { nav.popBackStack() },
                            onSaved = { count, step ->
                                nav.popBackStack()
                                scope.launch {
                                    snackbars.currentSnackbarData?.dismiss()
                                    val result = snackbars.showSnackbar(strings.putAway(count), strings.undo, duration = SnackbarDuration.Long)
                                    if (result == SnackbarResult.ActionPerformed) repository.undo(step)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
