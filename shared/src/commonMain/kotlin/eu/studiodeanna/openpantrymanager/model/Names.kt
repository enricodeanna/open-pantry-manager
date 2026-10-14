package eu.studiodeanna.openpantrymanager.model

private val ACCENTS = mapOf(
    'à' to 'a', 'á' to 'a', 'â' to 'a', 'ä' to 'a', 'ã' to 'a', 'å' to 'a',
    'è' to 'e', 'é' to 'e', 'ê' to 'e', 'ë' to 'e',
    'ì' to 'i', 'í' to 'i', 'î' to 'i', 'ï' to 'i',
    'ò' to 'o', 'ó' to 'o', 'ô' to 'o', 'ö' to 'o', 'õ' to 'o',
    'ù' to 'u', 'ú' to 'u', 'û' to 'u', 'ü' to 'u',
    'ç' to 'c', 'ñ' to 'n', 'ß' to 's',
)

private val NOT_ALPHANUMERIC = Regex("[^a-z0-9]+")

/**
 * What two names of food are compared by: lower case, without accents or punctuation, single
 * spaces. "Caffè " and "caffe" are the same food.
 */
fun nameKey(name: String): String =
    name.lowercase()
        .map { ACCENTS[it] ?: it }
        .joinToString("")
        .replace(NOT_ALPHANUMERIC, " ")
        .trim()

/** Whether [query] finds [name]: at the start of the name or of one of its words, ignoring case and accents. */
fun matchesName(name: String, query: String): Boolean {
    val q = nameKey(query)
    if (q.isEmpty()) return true
    val key = nameKey(name)
    return key.startsWith(q) || key.split(' ').any { it.startsWith(q) } || (q.length >= 3 && q in key)
}
