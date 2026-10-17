package eu.studiodeanna.openpantrymanager.voice

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechModelsTest {
    private val work: File = Files.createTempDirectory("speech").toFile()

    /** A model zip as Vosk publishes them: everything in a folder named after the model. */
    private fun zip(vararg entries: Pair<String, String>): File = File(work, "model.zip").also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, text) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    @Test
    fun aModelIsDownloadedCheckedAndUnpacked() {
        val file = zip("test-model/am/final.mdl" to "acoustic", "test-model/graph/Gr.fst" to "grammar")
        val model = SpeechModel("test-model", "unused", sha256(file), file.length())
        val models = SpeechModels(File(work, "speech"))
        var reported = 0L
        models.install(model, progress = { done, _ -> reported = done }, url = file.toURI().toString())
        assertTrue(models.has(model))
        assertEquals("acoustic", File(models.dir(model), "am/final.mdl").readText())
        assertEquals(file.length(), reported)
    }

    @Test
    fun aDownloadThatIsNotTheModelLeavesNothing() {
        val file = zip("test-model/am/final.mdl" to "acoustic")
        val model = SpeechModel("test-model", "unused", "0".repeat(64), file.length())
        val models = SpeechModels(File(work, "speech"))
        assertFailsWith<IOException> { models.install(model, url = file.toURI().toString()) }
        assertFalse(models.has(model))
        assertEquals(emptyList(), File(work, "speech").list()!!.toList())
    }

    @Test
    fun aZipWritingOutsideItsFolderIsRefused() {
        val file = zip("../escaped.txt" to "no")
        val model = SpeechModel("test-model", "unused", sha256(file), file.length())
        assertFailsWith<IOException> { SpeechModels(File(work, "speech")).install(model, url = file.toURI().toString()) }
        assertFalse(File(work, "escaped.txt").exists())
    }
}
