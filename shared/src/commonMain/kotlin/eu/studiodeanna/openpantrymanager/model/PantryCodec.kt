package eu.studiodeanna.openpantrymanager.model

import kotlinx.serialization.json.Json

/** Reads and writes the pantry file: plain, indented JSON, so it stays inspectable. */
object PantryCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(doc: PantryDocument): String = json.encodeToString(PantryDocument.serializer(), doc)

    /** Parses a pantry file; throws [IllegalArgumentException] for one this version cannot read. */
    fun decode(text: String): PantryDocument {
        val doc = json.decodeFromString(PantryDocument.serializer(), text)
        require(doc.format <= PantryDocument.FORMAT) { "Pantry saved by a newer version of the app" }
        return doc
    }
}
