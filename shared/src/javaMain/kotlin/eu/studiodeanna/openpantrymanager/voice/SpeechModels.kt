package eu.studiodeanna.openpantrymanager.voice

import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * The speech models on this device, in [folder]: each downloaded once, when voice commands are first
 * used in its language, checked against its SHA-256 and unpacked. A model counts as there only once
 * it is whole, so a download cut short is started again.
 */
class SpeechModels(private val folder: File) {
    /** Whether [model] is on the device, whole. */
    fun has(model: SpeechModel): Boolean = File(dir(model), READY).exists()

    /** The folder [model] is unpacked in. */
    fun dir(model: SpeechModel): File = File(folder, model.name)

    /**
     * Downloads [model] from [url] (its own, unless a test says otherwise) and unpacks it, calling
     * [progress] with the bytes so far; [cancelled] is asked between chunks. Throws [IOException] when it
     * fails, the download is not the model expected, or it was cancelled; what was saved is removed.
     */
    fun install(
        model: SpeechModel,
        progress: (done: Long, total: Long) -> Unit = { _, _ -> },
        cancelled: () -> Boolean = { false },
        url: String = model.url,
    ) {
        folder.mkdirs()
        val zip = File(folder, "${model.name}.zip.part")
        val unpacked = File(folder, "${model.name}.part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val connection = URL(url).openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: model.bytes
            connection.getInputStream().use { input ->
                zip.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        if (cancelled()) throw IOException("Download cancelled")
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        progress(done, total)
                    }
                }
            }
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (sha256 != model.sha256) throw IOException("The download is not ${model.name}: its SHA-256 is $sha256")
            unpack(zip, unpacked)
            // The zip holds the model in a folder of its own name.
            val root = unpacked.listFiles()?.singleOrNull { it.isDirectory } ?: unpacked
            val target = dir(model)
            target.deleteRecursively()
            if (!root.renameTo(target)) throw IOException("Could not put ${model.name} in place")
            File(target, READY).writeText(model.sha256)
        } finally {
            zip.delete()
            unpacked.deleteRecursively()
        }
    }

    /** Removes [model], to free its space. */
    fun remove(model: SpeechModel) {
        dir(model).deleteRecursively()
    }

    private fun unpack(zip: File, into: File) {
        into.deleteRecursively()
        into.mkdirs()
        val base = into.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { entries ->
            while (true) {
                val entry = entries.nextEntry ?: break
                val file = File(into, entry.name)
                // No entry may write outside the folder ("../" in a name).
                if (!file.canonicalPath.startsWith(base)) throw IOException("Unsafe path in the model: ${entry.name}")
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { entries.copyTo(it) }
                }
            }
        }
    }

    private companion object {
        /** Written last, when the model is whole. */
        const val READY = ".ready"
    }
}
