package eu.studiodeanna.openpantrymanager.checklist

import eu.studiodeanna.openpantrymanager.Messages
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Reads and writes Open Check Lists files: plain, indented JSON, as that app writes them. */
object ListCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(doc: ListDocument): String = json.encodeToString(ListDocument.serializer(), doc)

    /** Parses a list file; throws [InvalidListFile] when it is not one this version can read. */
    fun decode(text: String): ListDocument {
        val doc = try {
            json.decodeFromString(ListDocument.serializer(), text)
        } catch (e: SerializationException) {
            throw InvalidListFile(Messages.current.notAList, e)
        } catch (e: IllegalArgumentException) {
            throw InvalidListFile(Messages.current.notAList, e)
        }
        if (doc.format > ListDocument.FORMAT) {
            throw InvalidListFile(Messages.current.newerVersion)
        }
        return doc
    }
}

class InvalidListFile(message: String, cause: Throwable? = null) : Exception(message, cause)
