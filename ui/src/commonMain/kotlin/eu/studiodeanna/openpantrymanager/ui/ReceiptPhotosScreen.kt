package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.studiodeanna.openpantrymanager.PantryRepository
import eu.studiodeanna.openpantrymanager.receipt.OcrLine
import eu.studiodeanna.openpantrymanager.receipt.ReceiptEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A photo on the screen, and its reading: under way, done (its [lines]) or failed (why). */
private class Taken(val photo: ReceiptPhoto) {
    var job: Job? = null
    var lines by mutableStateOf<List<OcrLine>?>(null)
    var failed by mutableStateOf<String?>(null)
}

/**
 * The photos of a receipt: taken with the camera or picked from the gallery, one or several down a
 * long receipt. Each is read as soon as it is added, while the next is taken; then the receipt is
 * understood from them all, in order, and handed on for review.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptPhotosScreen(
    repository: PantryRepository,
    scanner: ReceiptScanner,
    snackbars: SnackbarHostState,
    onBack: () -> Unit,
    onRead: (List<ReceiptEntry>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val photos = remember { mutableStateListOf<Taken>() }
    var busy by remember { mutableStateOf(false) }
    var understanding by remember { mutableStateOf(false) }
    val texts = strings

    // Leaving, whichever way, lets the photos and the reader go.
    DisposableEffect(scanner) { onDispose { scanner.finish() } }

    fun say(message: String) {
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            snackbars.showSnackbar(message)
        }
    }

    fun add(photo: ReceiptPhoto) {
        val taken = Taken(photo)
        taken.job = scope.launch {
            try {
                taken.lines = scanner.read(photo)
            } catch (e: ReceiptScanFailed) {
                taken.failed = e.message ?: texts.scanFailed
            }
        }
        photos += taken
    }

    /** Runs [get] (the camera, the gallery) once at a time, and adds what it brings. */
    fun fetch(get: suspend () -> List<ReceiptPhoto>) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                get().forEach(::add)
            } catch (e: ReceiptScanFailed) {
                say(e.message ?: texts.scanFailed)
            } finally {
                busy = false
            }
        }
    }

    fun remove(taken: Taken) {
        taken.job?.cancel()
        photos.remove(taken)
        scanner.discard(taken.photo)
    }

    fun understand() {
        if (understanding) return
        understanding = true
        scope.launch {
            try {
                photos.toList().forEach { it.job?.join() }
                val pages = photos.mapNotNull { it.lines }
                val entries = if (pages.isEmpty()) emptyList() else repository.understandReceipt(pages)
                if (entries.isEmpty()) say(texts.nothingOnReceipt) else onRead(entries)
            } finally {
                understanding = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = strings.back) }
                },
                title = { Text(strings.receipt) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(strings.photoHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (photos.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        photos.forEachIndexed { index, taken -> PhotoTile(index + 1, taken, onRemove = { remove(taken) }) }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { fetch { listOfNotNull(scanner.takePhoto()) } }, enabled = !busy && !understanding) {
                        Icon(AppIcons.Camera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (photos.isEmpty()) strings.takePhoto else strings.takeAnotherPhoto)
                    }
                    OutlinedButton(onClick = { fetch { scanner.pickPhotos() } }, enabled = !busy && !understanding) {
                        Icon(AppIcons.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.fromGallery)
                    }
                }
                Button(
                    onClick = ::understand,
                    enabled = photos.any { it.failed == null } && !understanding,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (understanding) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text(strings.readingReceipt)
                    } else {
                        Text(strings.readReceipt)
                    }
                }
            }
        }
    }
}

/** A photo's tile: the photo, its number, how its reading goes, and a button to take it out. */
@Composable
private fun PhotoTile(number: Int, taken: Taken, onRemove: () -> Unit) {
    Column(Modifier.width(TILE_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Image(
                taken.photo.thumbnail,
                contentDescription = strings.photoNumber(number),
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(TILE_WIDTH).height(TILE_WIDTH * 4 / 3).clip(RoundedCornerShape(8.dp)),
            )
            Text(
                "$number",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(6.dp).size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape).padding(top = 2.dp),
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(32.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape),
            ) {
                Icon(AppIcons.Close, contentDescription = strings.removePhoto(number), modifier = Modifier.size(18.dp))
            }
        }
        val lines = taken.lines
        val failed = taken.failed
        Text(
            when {
                failed != null -> failed
                lines != null -> strings.linesRead(lines.size)
                else -> strings.readingPhoto
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (failed != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private val TILE_WIDTH = 96.dp
