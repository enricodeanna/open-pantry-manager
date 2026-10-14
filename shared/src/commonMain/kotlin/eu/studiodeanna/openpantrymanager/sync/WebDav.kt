package eu.studiodeanna.openpantrymanager.sync

import eu.studiodeanna.openpantrymanager.Messages
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.CancellationException

/** One file or folder in a PROPFIND answer; [href] is as the server wrote it, without a trailing slash. */
internal class DavEntry(private val href: String, val isFolder: Boolean) {
    /** The entry's own name, decoded. */
    val name: String get() = href.substringAfterLast('/').decodeURLPart()

    /** Its whole path on the server, decoded. */
    val path: String get() = href.decodeURLPart()
}

/** What Nextcloud's account and public-share WebDAV answers have in common, read without an XML parser. */
internal object WebDav {
    private val RESPONSE = Regex("""<(?:\w+:)?response>(.*?)</(?:\w+:)?response>""", RegexOption.DOT_MATCHES_ALL)
    private val HREF = Regex("""<(?:\w+:)?href>([^<]*)</(?:\w+:)?href>""")
    private val COLLECTION = Regex("""<(?:\w+:)?collection\s*/>""")
    private val SERVER_MESSAGE = Regex("""<(?:\w+:)?message>(.*?)</(?:\w+:)?message>""", RegexOption.DOT_MATCHES_ALL)

    /** The entries of a PROPFIND answer, the folder asked about included. */
    fun entries(body: String): List<DavEntry> = RESPONSE.findAll(body).mapNotNull { match ->
        val entry = match.groupValues[1]
        val href = HREF.find(entry)?.groupValues?.get(1)?.trimEnd('/') ?: return@mapNotNull null
        DavEntry(href, COLLECTION.containsMatchIn(entry))
    }.toList()

    /** Whether a depth-0 PROPFIND answer describes a folder. */
    fun isFolder(body: String): Boolean = COLLECTION.containsMatchIn(body)

    /** The reason Nextcloud gives in a refusal's Sabre error document, which names the real cause; null if none. */
    suspend fun serverMessage(response: HttpResponse): String? =
        runCatching { SERVER_MESSAGE.find(response.bodyAsText())?.groupValues?.get(1)?.trim() }
            .getOrNull()?.takeIf { it.isNotEmpty() }
}

/** Runs [block], reporting any failure other than a [RemoteException] as [server] being out of reach. */
internal inline fun <T> guarded(server: String, block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: RemoteException) {
    throw e
} catch (e: Exception) {
    throw RemoteException(Messages.current.cantReach(server), e)
}
