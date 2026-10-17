package eu.studiodeanna.openpantrymanager.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import eu.studiodeanna.openpantrymanager.Messages
import eu.studiodeanna.openpantrymanager.ui.VoiceFailed
import eu.studiodeanna.openpantrymanager.ui.VoiceInput
import eu.studiodeanna.openpantrymanager.voice.SpeechModels
import eu.studiodeanna.openpantrymanager.voice.VoiceLanguage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Voice commands on Android: the microphone, heard by Vosk (Apache 2.0) on the phone, twice over: freely,
 * and listening for the words of commands and food only, which it hears better (see
 * [eu.studiodeanna.openpantrymanager.voice.understandVoice]). A language's model is downloaded once,
 * into the app's files; nothing that is said leaves the phone. The model is loaded while the voice
 * dialog is open, and let go after.
 */
class VoskVoiceInput(private val activity: ComponentActivity) : VoiceInput {
    // Looked up when first needed: while the activity is being made, it has no files folder yet.
    private val models by lazy { SpeechModels(File(activity.filesDir, "speech")) }
    private var permission: CompletableDeferred<Boolean>? = null

    // Made before the activity starts, as Android requires of what waits for an answer.
    private val askPermission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission?.complete(it)
    }

    /** The loaded model, and whether one is listening with it, which it must outlive. */
    private val lock = Any()
    private var model: Model? = null
    private var modelName: String? = null
    private var listening = false
    private var closeWhenDone = false

    /** For tests: the next sentence is "heard" from this WAV file (16 kHz, mono, 16 bits) instead of the microphone. */
    var nextAudio: File? = null

    override fun hasModel(language: VoiceLanguage): Boolean = models.has(language.model)

    override suspend fun download(language: VoiceLanguage, progress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        try {
            models.install(
                language.model,
                progress = { done, total -> progress((done.toFloat() / total).coerceIn(0f, 1f)) },
                cancelled = { !isActive },
            )
        } catch (e: IOException) {
            Log.w(TAG, "The speech model could not be downloaded", e)
            throw VoiceFailed(Messages.current.speechModelFailed, e)
        }
    }

    override suspend fun listen(language: VoiceLanguage, words: List<String>, partial: (String) -> Unit): List<String>? {
        val audio = nextAudio?.also { nextAudio = null }
        if (audio == null && ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val answer = CompletableDeferred<Boolean>().also { permission = it }
            askPermission.launch(Manifest.permission.RECORD_AUDIO)
            if (!answer.await()) throw VoiceFailed(Messages.current.microphoneNeeded)
        }
        return withContext(Dispatchers.IO) {
            val model = synchronized(lock) {
                try {
                    load(language).also { listening = true }
                } catch (e: IOException) {
                    Log.w(TAG, "The speech model could not be loaded", e)
                    throw VoiceFailed(Messages.current.speechFailed, e)
                }
            }
            try {
                val grammar = JsonArray((words + UNKNOWN).map(::JsonPrimitive)).toString()
                Recognizer(model, SAMPLE_RATE).use { free ->
                    Recognizer(model, SAMPLE_RATE, grammar).use { limited ->
                        (audio?.let(::WavSource) ?: MicSource()).use { source ->
                            hear(source, free, limited, partial).also { heard ->
                                // Test builds log what was heard, both ways, to tune the commands with.
                                if (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) Log.d(TAG, "Heard: $heard")
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Listening failed", e)
                throw VoiceFailed(Messages.current.speechFailed, e)
            } finally {
                synchronized(lock) {
                    listening = false
                    if (closeWhenDone) close()
                }
            }
        }
    }

    /**
     * Feeds what [source] hears to both recognizers until the free one hears a sentence end, the
     * source ends, or time is up; meanwhile what it hears so far goes to [partial].
     */
    private suspend fun hear(source: Source, free: Recognizer, limited: Recognizer, partial: (String) -> Unit): List<String>? =
        withContext(Dispatchers.IO) {
            val buffer = ByteArray(BUFFER_BYTES)
            val until = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
            while (isActive && SystemClock.elapsedRealtime() < until) {
                val read = source.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                limited.acceptWaveForm(buffer, read)
                if (free.acceptWaveForm(buffer, read)) {
                    val sentence = field(free.result, "text")
                    if (sentence.isNotBlank()) return@withContext listOf(sentence, field(limited.finalResult, "text"))
                } else {
                    field(free.partialResult, "partial").takeIf { it.isNotBlank() }?.let(partial)
                }
            }
            listOf(field(free.finalResult, "text"), field(limited.finalResult, "text")).filter { it.isNotBlank() }.ifEmpty { null }
        }

    override fun finish() {
        synchronized(lock) {
            // A recognizer still at work keeps it until it stops.
            if (listening) closeWhenDone = true else close()
        }
    }

    private fun close() {
        model?.close()
        model = null
        modelName = null
        closeWhenDone = false
    }

    /** The model of [language], loaded once while the dialog is open. */
    private fun load(language: VoiceLanguage): Model {
        closeWhenDone = false
        model?.takeIf { modelName == language.model.name }?.let { return it }
        model?.close()
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        return Model(models.dir(language.model).path).also {
            model = it
            modelName = language.model.name
        }
    }

    /** A field of what Vosk answers, JSON such as {"text": "ho mangiato una mela"}. */
    private fun field(hypothesis: String, name: String): String =
        runCatching { Json.parseToJsonElement(hypothesis).jsonObject[name]?.jsonPrimitive?.content }.getOrNull().orEmpty()

    /** Where the sound comes from: 16 kHz, mono, 16-bit samples. */
    private interface Source : Closeable {
        /** Reads into [buffer]; the bytes read, or -1 at the end. */
        fun read(buffer: ByteArray): Int
    }

    /** The microphone, as Android hands it to speech recognition. */
    @SuppressLint("MissingPermission") // Asked for in listen() before.
    private class MicSource : Source {
        private val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE.toInt(),
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(BUFFER_BYTES * 2, AudioRecord.getMinBufferSize(SAMPLE_RATE.toInt(), AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)),
        )

        init {
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                throw IOException("The microphone could not be opened")
            }
            record.startRecording()
        }

        override fun read(buffer: ByteArray): Int = record.read(buffer, 0, buffer.size)

        override fun close() {
            runCatching { record.stop() }
            record.release()
        }
    }

    /** A WAV file of 16 kHz, mono, 16-bit samples, for tests. */
    private class WavSource(file: File) : Source {
        // The header, 44 bytes, is not sound.
        private val input: InputStream = file.inputStream().buffered().apply { skip(WAV_HEADER) }

        override fun read(buffer: ByteArray): Int = input.read(buffer)

        override fun close() = input.close()
    }

    private companion object {
        const val TAG = "Voice"
        const val SAMPLE_RATE = 16_000f
        /** A tenth of a second of sound. */
        const val BUFFER_BYTES = 3_200
        /** Long enough for a sentence, said slowly; then listening stops by itself. */
        const val TIMEOUT_MILLIS = 10_000L
        const val WAV_HEADER = 44L
        /** What the recognizer listening for words writes for any other word. */
        const val UNKNOWN = "[unk]"
    }
}
