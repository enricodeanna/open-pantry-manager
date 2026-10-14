package eu.studiodeanna.openpantrymanager.sync

import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.Serializable

/**
 * A shared-file link as the user pasted it, plus the share password if it has one. [fileName] is the
 * list's own file when the link is to a shared folder holding several lists. [expires] is the day
 * (yyyy-mm-dd) the server ends a link the app created, when the server sets one.
 */
@Serializable
data class ShareLink(
    val url: String,
    val password: String? = null,
    val fileName: String? = null,
    val expires: String? = null,
)

sealed interface ParsedLink {
    /** A Nextcloud public share, `https://cloud.example.com/s/<token>`, optionally naming one list file in it. */
    data class Nextcloud(val server: String, val token: String, val file: String? = null) : ParsedLink
    /** [resourceKey] comes from older links' `resourcekey=` parameter; Drive requires it for those files. */
    data class GoogleDrive(val fileId: String, val resourceKey: String? = null) : ParsedLink
    data object Unrecognized : ParsedLink
}

object ShareLinks {
    private val nextcloud = Regex("""^(https?://[^?#]+?)(?:/index\.php)?/s/([A-Za-z0-9]+)/?(?:download/?)?(?:[?#].*)?$""")
    private val googleFile = Regex("""^https://(?:drive|docs)\.google\.com/(?:file|document|spreadsheets)/d/([\w-]+)""")
    private val googleOpen = Regex("""^https://drive\.google\.com/(?:open|uc)\?(?:.*&)?id=([\w-]+)""")

    private val resourceKey = Regex("""[?&]resourcekey=([\w-]+)""")
    private val fileParam = Regex("""[?&]file=([^&#]+)""")

    fun parse(url: String): ParsedLink {
        val trimmed = url.trim()
        val key = resourceKey.find(trimmed)?.groupValues?.get(1)
        googleFile.find(trimmed)?.let { return ParsedLink.GoogleDrive(it.groupValues[1], key) }
        googleOpen.find(trimmed)?.let { return ParsedLink.GoogleDrive(it.groupValues[1], key) }
        nextcloud.matchEntire(trimmed)?.let {
            val file = fileParam.find(trimmed)?.groupValues?.get(1)?.decodeURLQueryComponent(plusIsSpace = true)
            return ParsedLink.Nextcloud(it.groupValues[1], it.groupValues[2], file)
        }
        return ParsedLink.Unrecognized
    }

    /** Moves a `file=` in a pasted Nextcloud address into [ShareLink.fileName], so stored links are uniform. */
    fun normalize(link: ShareLink): ShareLink {
        val parsed = parse(link.url) as? ParsedLink.Nextcloud ?: return link.copy(url = link.url.trim())
        return link.copy(url = "${parsed.server}/s/${parsed.token}", fileName = link.fileName ?: parsed.file)
    }

    /** The address to send to others. For a list in a shared folder it names the list's file. */
    fun shareable(link: ShareLink): String {
        val file = link.fileName ?: return link.url
        if (parse(link.url) !is ParsedLink.Nextcloud) return link.url
        return "${link.url}?file=${file.encodeURLParameter()}"
    }
}

/** The highest [shareFileName] number tried before a name counts as taken, "Dispensa (99).pantry". */
internal const val MAX_FILE_NUMBER = 99

private val NOT_IN_FILE_NAMES = Regex("""[/\\:*?"<>|\u0000-\u001f]""")

/**
 * The file name for [title] with [extension]: the title with characters that are not allowed in file
 * names replaced, and for [n] > 1 the number Nextcloud itself adds to duplicates, "Dispensa (2).pantry".
 */
fun shareFileName(title: String, extension: String, n: Int = 1): String {
    val base = title
        .replace(NOT_IN_FILE_NAMES, "-")
        .trim()
        .trimStart('.')
        .take(100)
        .trim()
        .ifEmpty { "Pantry" }
    return if (n <= 1) "$base$extension" else "$base ($n)$extension"
}
