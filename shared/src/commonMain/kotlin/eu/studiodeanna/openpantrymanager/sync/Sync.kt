package eu.studiodeanna.openpantrymanager.sync

import eu.studiodeanna.openpantrymanager.Messages
import eu.studiodeanna.openpantrymanager.checklist.InvalidListFile
import eu.studiodeanna.openpantrymanager.checklist.ListCodec
import eu.studiodeanna.openpantrymanager.checklist.ListDocument
import io.ktor.client.HttpClient

/** How a kind of shared file is read, written and merged. */
interface DocumentFormat<T> {
    /** Parses the file; a [RemoteException] for the user when it is not one this app can use. */
    fun decode(text: String): T
    fun encode(document: T): String
    /** Combines two copies; merging a copy with itself gives its canonical form. */
    fun merge(a: T, b: T): T
}

/**
 * Brings [local] and the remote file together: reads the file, merges, and writes the merge back only
 * if it adds something. A concurrent writer makes the conditional write fail, and the round repeats.
 * Returns the merged document, which the caller merges into whatever the user edited meanwhile.
 */
suspend fun <T> syncDocument(remote: RemoteFile, local: T, format: DocumentFormat<T>, attempts: Int = 4): T {
    repeat(attempts) {
        val content = remote.read()
        val remoteDoc = content.text?.takeIf { it.isNotBlank() }?.let(format::decode)
        val merged = if (remoteDoc == null) format.merge(local, local) else format.merge(local, remoteDoc)
        if (remoteDoc != null && merged == format.merge(remoteDoc, remoteDoc)) return merged
        when (remote.write(format.encode(merged), content.version)) {
            WriteResult.Written -> return merged
            WriteResult.Conflict -> Unit
        }
    }
    throw RemoteException(Messages.current.keptChanging)
}

/** Open Check Lists' list files, as [syncDocument] handles them. */
object ListFormat : DocumentFormat<ListDocument> {
    override fun decode(text: String): ListDocument {
        // A pantry with no food reads as an empty list; written back as a list, it would be lost.
        if (SharedPantryCodec.isPantry(text)) throw RemoteException(Messages.current.notAList)
        return try {
            ListCodec.decode(text)
        } catch (e: InvalidListFile) {
            throw RemoteException(e.message ?: Messages.current.notAList, e)
        }
    }

    override fun encode(document: ListDocument) = ListCodec.encode(document)
    // Named in full: plain merge here would be this function.
    override fun merge(a: ListDocument, b: ListDocument) = eu.studiodeanna.openpantrymanager.checklist.merge(a, b)
}

/** Syncs a shopping list; see [syncDocument]. */
suspend fun syncWith(remote: RemoteFile, local: ListDocument, attempts: Int = 4): ListDocument =
    syncDocument(remote, local, ListFormat, attempts)

/** The remote file behind a link, or a user-facing error when the link is not usable. */
fun remoteFileFor(link: ShareLink, http: HttpClient): RemoteFile =
    when (val parsed = ShareLinks.parse(link.url)) {
        is ParsedLink.Nextcloud -> NextcloudPublicFile(NextcloudShare(parsed, link.password, http), link.fileName)
        is ParsedLink.GoogleDrive -> throw RemoteException(Messages.current.driveNotYet)
        ParsedLink.Unrecognized -> throw RemoteException(Messages.current.notAShareLink)
    }

/** The Nextcloud share behind a link, or null for other kinds of links. */
fun nextcloudShareFor(link: ShareLink, http: HttpClient): NextcloudShare? =
    (ShareLinks.parse(link.url) as? ParsedLink.Nextcloud)?.let { NextcloudShare(it, link.password, http) }
