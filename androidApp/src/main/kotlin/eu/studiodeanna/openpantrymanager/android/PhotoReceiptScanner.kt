package eu.studiodeanna.openpantrymanager.android

import android.content.ActivityNotFoundException
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import eu.studiodeanna.openpantrymanager.Messages
import eu.studiodeanna.openpantrymanager.ocr.OnnxTextReader
import eu.studiodeanna.openpantrymanager.ocr.Picture
import eu.studiodeanna.openpantrymanager.receipt.OcrLine
import eu.studiodeanna.openpantrymanager.receipt.ReceiptParser
import eu.studiodeanna.openpantrymanager.ui.ReceiptPhoto
import eu.studiodeanna.openpantrymanager.ui.ReceiptScanFailed
import eu.studiodeanna.openpantrymanager.ui.ReceiptScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Receipts on Android: photos from the phone's camera app or its gallery, read on the phone by
 * PaddleOCR's networks on ONNX Runtime, which are part of the app. Nothing leaves the phone.
 *
 * The photos are kept in the app's cache until the receipt is read or given up.
 */
class PhotoReceiptScanner(private val activity: ComponentActivity) : ReceiptScanner {
    private var tookPhoto: CompletableDeferred<Boolean>? = null
    private var picked: CompletableDeferred<List<Uri>>? = null

    // Registered before the activity starts, as Android requires of result launchers.
    private val camera = activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { tookPhoto?.complete(it) }
    private val gallery = activity.registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED)) {
        picked?.complete(it)
    }

    private val folder get() = File(activity.cacheDir, "receipts").apply { mkdirs() }
    private val debuggable get() = activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** One photo is read at a time: the reader takes much of the processor and memory. */
    private val reading = Mutex()
    private var reader: OnnxTextReader? = null

    /** For tests: the next photos "taken" are these pictures, instead of the camera's. */
    var nextImages = ArrayDeque<File>()

    override suspend fun takePhoto(): ReceiptPhoto? {
        val file = newFile()
        nextImages.removeFirstOrNull()?.let { image ->
            image.copyTo(file, overwrite = true)
            return photo(file)
        }
        val reply = CompletableDeferred<Boolean>().also { tookPhoto = it }
        try {
            camera.launch(FileProvider.getUriForFile(activity, "${activity.packageName}.files", file))
        } catch (e: ActivityNotFoundException) {
            throw ReceiptScanFailed(Messages.current.noCameraApp, e)
        }
        if (!reply.await() || file.length() == 0L) {
            file.delete()
            return null
        }
        return photo(file)
    }

    override suspend fun pickPhotos(): List<ReceiptPhoto> {
        val reply = CompletableDeferred<List<Uri>>().also { picked = it }
        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        val uris = reply.await()
        return withContext(Dispatchers.IO) {
            uris.map { uri ->
                val file = newFile()
                try {
                    activity.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
                    photo(file)
                } catch (e: Exception) {
                    file.delete()
                    throw ReceiptScanFailed(Messages.current.photoUnreadable, e)
                }
            }
        }
    }

    override suspend fun read(photo: ReceiptPhoto): List<OcrLine> = withContext(Dispatchers.Default) {
        reading.withLock {
            try {
                val started = SystemClock.elapsedRealtime()
                val picture = picture(File(photo.id))
                val decoded = SystemClock.elapsedRealtime()
                val reader = reader ?: openReader().also { reader = it }
                val opened = SystemClock.elapsedRealtime()
                val lines = reader.read(picture)
                // Test builds log what was read, row by row, to tune the receipt reader with.
                if (debuggable) {
                    Log.d(
                        "Receipt",
                        "${picture.width}x${picture.height}: decoded in ${decoded - started} ms, reader opened in " +
                            "${opened - decoded} ms, read in ${SystemClock.elapsedRealtime() - opened} ms",
                    )
                    ReceiptParser.rows(lines).forEach { Log.d("Receipt", it) }
                }
                lines
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Receipt", "A photo could not be read", e)
                throw ReceiptScanFailed(Messages.current.photoUnreadable, e)
            } catch (e: OutOfMemoryError) {
                Log.w("Receipt", "A photo could not be read", e)
                throw ReceiptScanFailed(Messages.current.photoUnreadable, e)
            }
        }
    }

    override fun discard(photo: ReceiptPhoto) {
        File(photo.id).delete()
    }

    override fun finish() {
        // The photos of this receipt only: the next one's may be taken before reading this one stops.
        val photos = folder.listFiles().orEmpty()
        // After any reading still under way; the screen that asked is gone already.
        activity.lifecycleScope.launch(Dispatchers.Default + NonCancellable) {
            reading.withLock {
                reader?.close()
                reader = null
                photos.forEach { it.delete() }
            }
        }
    }

    private fun newFile() = File(folder, "photo-${System.currentTimeMillis()}-${counter++}.jpg")

    private fun openReader(): OnnxTextReader {
        fun asset(name: String) = activity.assets.open("ocr/$name").use { it.readBytes() }
        return OnnxTextReader(asset("det.onnx"), asset("rec.onnx"), asset("characters.txt").decodeToString(), THREADS)
    }

    /** The photo in [file] and a small copy to show. */
    private suspend fun photo(file: File): ReceiptPhoto = withContext(Dispatchers.IO) {
        val thumbnail = decode(file, THUMBNAIL_SIDE) ?: run {
            file.delete()
            throw ReceiptScanFailed(Messages.current.photoUnreadable)
        }
        ReceiptPhoto(file.path, thumbnail.asImageBitmap())
    }

    /** The photo in [file], at a size the reader makes the most of, upright. */
    private fun picture(file: File): Picture {
        val bitmap = decode(file, READ_SIDE) ?: throw ReceiptScanFailed(Messages.current.photoUnreadable)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return Picture(bitmap.width, bitmap.height, pixels).also { bitmap.recycle() }
    }

    /**
     * The image in [file], made smaller by halves while its longer side stays at least [side] pixels,
     * and turned as the camera noted it was held. Null if it is not an image.
     */
    private fun decode(file: File, side: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val degrees = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    private companion object {
        var counter = 0
        const val MAX_PICKED = 10
        /** The reader works on photos of up to 2000 pixels a side. */
        const val READ_SIDE = 2000
        const val THUMBNAIL_SIDE = 320
        /**
         * Threads for the networks. On a phone with two fast cores and six slow ones (moto g 5G), four
         * took half as long again as two: the slow cores hold the others up.
         */
        const val THREADS = 2
    }
}
