package eu.studiodeanna.openpantrymanager.ui

import androidx.compose.ui.graphics.ImageBitmap
import eu.studiodeanna.openpantrymanager.receipt.OcrLine

/** A photo of a receipt, taken or picked, with a small copy of it to show. */
class ReceiptPhoto(val id: String, val thumbnail: ImageBitmap)

/**
 * Takes photos of receipts, with the camera or from the gallery, and reads the text on them, on the
 * device. Each platform that can do so provides one.
 */
interface ReceiptScanner {
    /** A photo taken with the camera, or null if the user went back without one. */
    suspend fun takePhoto(): ReceiptPhoto?

    /** Photos picked from the gallery, in the order picked; none if the user went back. */
    suspend fun pickPhotos(): List<ReceiptPhoto>

    /** The lines of text on [photo]. Throws [ReceiptScanFailed] when it cannot be read. */
    suspend fun read(photo: ReceiptPhoto): List<OcrLine>

    /** Forgets a photo the user took out. */
    fun discard(photo: ReceiptPhoto)

    /** The receipt was read, or given up: forgets its photos and frees the memory reading took. */
    fun finish()
}

/** A photo that could not be taken or read, with a [message] for the user. */
class ReceiptScanFailed(message: String, cause: Throwable? = null) : Exception(message, cause)
