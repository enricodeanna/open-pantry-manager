package eu.studiodeanna.openpantrymanager.ocr

import eu.studiodeanna.openpantrymanager.receipt.ReceiptParser
import eu.studiodeanna.openpantrymanager.receipt.guessName
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reads photos of receipts with the networks the Android app ships, on ONNX Runtime for the desktop.
 * The receipts are made up and printed by a computer, then tilted, blurred and shaded as phone photos.
 */
class TextReaderTest {
    private val models = File(System.getProperty("ocr.models"))
    private val reader = OnnxTextReader(
        File(models, "det.onnx").readBytes(),
        File(models, "rec.onnx").readBytes(),
        File(models, "characters.txt").readText(),
        threads = 4,
    )

    @AfterTest
    fun close() = reader.close()

    private fun read(name: String) = reader.read(picture(name))

    private fun picture(name: String): Picture {
        val image = ImageIO.read(javaClass.getResource("/receipts/$name"))
        return Picture(image.width, image.height, image.getRGB(0, 0, image.width, image.height, null, 0, image.width))
    }

    /** The products as guessed names with their counts, which a misread space or dot does not change. */
    private fun products(pages: List<String>) =
        ReceiptParser.parsePages(pages.map(::read)).map { "${it.quantity} ${guessName(it.text)}" }

    @Test
    fun readsTiltedShadedAndSmallPhotos() {
        val expected = listOf(
            "1 Latte friuli",
            "1 Banane",
            "2 Yogurt magro bianco",
            "1 Prosc crudo daniele",
            "1 Passata pomodoro",
            "1 Acqua frizz",
            "1 Shopper bio",
        )
        for (photo in listOf("tilted.jpg", "shadow.jpg", "small.jpg")) {
            val lines = ReceiptParser.parse(read(photo))
            // The prices too, crossed zeros and all.
            assertEquals(listOf(1.15, 1.89, 1.78, 3.49, 0.99, 2.34, 0.10), lines.map { it.price }, photo)
            assertEquals(listOf(1, 1, 2, 1, 1, 1, 1), lines.map { it.quantity }, photo)
            // In a photo this small, print 10 pixels high, a letter here and there is read as a digit ("SHOPPER B10").
            if (photo != "small.jpg") assertEquals(expected, lines.map { "${it.quantity} ${guessName(it.text)}" }, photo)
        }
    }

    private val longReceipt = listOf(
        "LATTE INTERO FRESCO 1L", "UOVA FRESCHE X6", "BURRO 250G", "PANE COMUNE", "MOZZARELLA 125G",
        "PARMIGIANO REGGIANO 300G", "SPAGHETTI N.5 500G", "RISO CARNAROLI 1KG", "OLIO EXTRA VERGINE 1L",
        "POMODORI PELATI 400G", "TONNO ALL'OLIO 3X80G", "MELE GOLDEN", "INSALATA ICEBERG", "CAROTE 1KG",
        "PATATE 2KG", "PETTO DI POLLO", "PROSCIUTTO COTTO 100G", "YOGURT GRECO 170G", "BISCOTTI FROLLINI 700G",
        "CAFFE MACINATO 250G", "ZUCCHERO 1KG", "FARINA 00 1KG", "DETERSIVO PIATTI", "CARTA IGIENICA X4", "SHOPPER BIO",
    )

    /** The long receipt read from [pages] is the one printed, but for spaces the reader may run together on a blurred photo. */
    private fun assertLongReceipt(pages: List<String>) {
        fun compact(text: String) = text.uppercase().filter { it.isLetterOrDigit() }
        assertEquals(
            longReceipt.map { (if (it.startsWith("MOZZARELLA")) "2 " else "1 ") + compact(it) },
            ReceiptParser.parsePages(pages.map(::read)).map { "${it.quantity} ${compact(it.text)}" },
            pages.joinToString("\n\n") { ReceiptParser.rows(read(it)).joinToString("\n") },
        )
    }

    @Test
    fun joinsThreeOverlappingPhotosOfALongReceipt() = assertLongReceipt(listOf("long-1.jpg", "long-2.jpg", "long-3.jpg"))

    /** A photo as large as a phone's camera gives, after the app makes it 2000 pixels long. */
    @Test
    fun readsALongReceiptInOneLargePhoto() = assertLongReceipt(listOf("long-whole.jpg"))
}
