package eu.studiodeanna.openpantrymanager.costs

import eu.studiodeanna.openpantrymanager.Messages
import eu.studiodeanna.openpantrymanager.model.Stamped
import eu.studiodeanna.openpantrymanager.model.mergeStamped
import eu.studiodeanna.openpantrymanager.model.prunedTombstones
import eu.studiodeanna.openpantrymanager.sync.DocumentFormat
import eu.studiodeanna.openpantrymanager.sync.RemoteException
import kotlinx.datetime.LocalDate
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What was paid for some food: [quantity] pieces of [food] (the pantry's name for it) for [cents] in
 * all, on [day], at [shop] if known. [amount] is how much that was in all, by weight or volume, when the
 * package size or the scales said: what prices per kilogram or litre are reckoned from.
 */
@Serializable
data class Purchase(
    override val id: String,
    val food: String,
    val quantity: Int,
    val cents: Long,
    val day: LocalDate,
    val amount: Measure? = null,
    val shop: String? = null,
    override val at: Long,
    override val by: String,
    override val deleted: Boolean = false,
) : Stamped

/**
 * What the household paid for its food, purchase by purchase. Kept apart from the pantry, whose food
 * leaves when it is finished, and in a file of its own when shared, so that the history, which only
 * grows, does not travel with every change of the pantry.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CostsDocument(
    /** Marks the file as the costs of a pantry. */
    @EncodeDefault val kind: String = KIND,
    @EncodeDefault val format: Int = FORMAT,
    val purchases: List<Purchase> = emptyList(),
) {
    companion object {
        const val KIND = "open-pantry-manager-costs"
        const val FORMAT = 1
        /** Next to the pantry's file, with this in place of ".pantry". */
        const val EXTENSION = ".costs"
    }
}

fun CostsDocument.livePurchases(): List<Purchase> = purchases.filterNot { it.deleted }

fun CostsDocument.merge(other: CostsDocument) =
    CostsDocument(format = maxOf(format, other.format), purchases = mergeStamped(purchases, other.purchases))

fun CostsDocument.pruned(now: Long) = copy(purchases = purchases.prunedTombstones(now))

/** Reads and writes costs files, on the phone and shared: plain JSON. */
object CostsCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(document: CostsDocument): String = json.encodeToString(CostsDocument.serializer(), document)

    /** Parses a costs file; a [RemoteException] for the user when it is not one this version can read. */
    fun decode(text: String): CostsDocument {
        val kind = runCatching { json.parseToJsonElement(text).jsonObject["kind"]?.jsonPrimitive?.content }.getOrNull()
        if (kind != CostsDocument.KIND) throw RemoteException(Messages.current.notAPantry)
        val document = try {
            json.decodeFromString(CostsDocument.serializer(), text)
        } catch (e: SerializationException) {
            throw RemoteException(Messages.current.notAPantry, e)
        } catch (e: IllegalArgumentException) {
            throw RemoteException(Messages.current.notAPantry, e)
        }
        if (document.format > CostsDocument.FORMAT) throw RemoteException(Messages.current.newerPantry)
        return document
    }
}

/** How costs files are synced. */
object CostsFormat : DocumentFormat<CostsDocument> {
    override fun decode(text: String) = CostsCodec.decode(text)
    override fun encode(document: CostsDocument) = CostsCodec.encode(document)
    override fun merge(a: CostsDocument, b: CostsDocument) = a.merge(b)
}
