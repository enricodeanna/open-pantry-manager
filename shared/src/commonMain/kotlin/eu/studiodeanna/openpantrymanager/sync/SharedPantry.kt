package eu.studiodeanna.openpantrymanager.sync

import eu.studiodeanna.openpantrymanager.Messages
import eu.studiodeanna.openpantrymanager.memory.Memory
import eu.studiodeanna.openpantrymanager.memory.merge
import eu.studiodeanna.openpantrymanager.model.PantryDocument
import eu.studiodeanna.openpantrymanager.model.PantryItem
import eu.studiodeanna.openpantrymanager.model.Space
import eu.studiodeanna.openpantrymanager.model.compareStamps
import eu.studiodeanna.openpantrymanager.model.mergeStamped
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A pantry as a shared file holds it: its spaces and food, what the app has learned, and the shopping
 * list the household uses. Every phone sharing it keeps a copy, and merges.
 *
 * Its file ends in [EXTENSION], not ".json": Open Check Lists takes every .json file in a shared folder
 * for a list, and an empty pantry reads as an empty list there; saved as a list, it would lose all
 * but its items.
 */
@Serializable
data class SharedPantry(
    /** Marks the file as a pantry, so that no other file is taken for one, nor written over. */
    val kind: String = KIND,
    val format: Int = FORMAT,
    val spaces: List<Space> = emptyList(),
    val items: List<PantryItem> = emptyList(),
    val memory: Memory = Memory(),
    val shoppingList: SharedShoppingList? = null,
) {
    val pantry: PantryDocument get() = PantryDocument(spaces = spaces, items = items)

    companion object {
        const val KIND = "open-pantry-manager"
        /** 2: items have the day they were bought. An older app refuses the file rather than dropping it. */
        const val FORMAT = 2
        const val EXTENSION = ".pantry"
    }
}

/** The shopping list of a shared pantry, as last linked or unlinked ([link] null) on any of its phones. */
@Serializable
data class SharedShoppingList(val link: ShareLink?, val at: Long, val by: String)

/** Two copies of a shared pantry as one: per space, item and learned thing the most recent change wins. */
fun SharedPantry.merge(other: SharedPantry): SharedPantry {
    val list = listOfNotNull(shoppingList, other.shoppingList).maxWithOrNull { a, b ->
        compareStamps(a.at, a.by, b.at, b.by).takeIf { it != 0 } ?: a.link.toString().compareTo(b.link.toString())
    }
    return SharedPantry(
        format = maxOf(format, other.format),
        spaces = mergeStamped(spaces, other.spaces),
        items = mergeStamped(items, other.items),
        memory = memory.merge(other.memory),
        shoppingList = list,
    )
}

/** Reads and writes pantry files: plain, indented JSON, like the pantry kept on the phone. */
object SharedPantryCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(pantry: SharedPantry): String = json.encodeToString(SharedPantry.serializer(), pantry)

    /** Parses a pantry file; a [RemoteException] for the user when it is not one this version can read. */
    fun decode(text: String): SharedPantry {
        if (!isPantry(text)) throw RemoteException(Messages.current.notAPantry)
        val pantry = try {
            json.decodeFromString(SharedPantry.serializer(), text)
        } catch (e: SerializationException) {
            throw RemoteException(Messages.current.notAPantry, e)
        } catch (e: IllegalArgumentException) {
            throw RemoteException(Messages.current.notAPantry, e)
        }
        if (pantry.format > SharedPantry.FORMAT) throw RemoteException(Messages.current.newerPantry)
        return pantry
    }

    /** Whether [text] is a pantry file: JSON marked as one. */
    fun isPantry(text: String): Boolean = try {
        json.parseToJsonElement(text).jsonObject["kind"]?.jsonPrimitive?.content == SharedPantry.KIND
    } catch (_: IllegalArgumentException) {
        false
    }
}

/** How [syncDocument] reads, writes and merges pantry files. */
object PantryFormat : DocumentFormat<SharedPantry> {
    override fun decode(text: String) = SharedPantryCodec.decode(text)
    override fun encode(document: SharedPantry) = SharedPantryCodec.encode(document)
    override fun merge(a: SharedPantry, b: SharedPantry) = a.merge(b)
}
