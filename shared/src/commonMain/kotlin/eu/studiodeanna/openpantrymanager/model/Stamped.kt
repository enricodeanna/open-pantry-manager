package eu.studiodeanna.openpantrymanager.model

/**
 * Something kept in a document that two devices may change apart: it carries the stamp (`at`, `by`)
 * of its last change, and is kept as a tombstone when deleted, so copies can always be merged.
 */
interface Stamped {
    val id: String
    val at: Long
    val by: String
    val deleted: Boolean
}

/**
 * Merges two versions of a set of stamped things: per id the most recent change wins. The result
 * does not depend on argument order, and merging with a copy already contained in the other changes
 * nothing, so it is safe to merge repeatedly.
 */
fun <T : Stamped> mergeStamped(a: List<T>, b: List<T>): List<T> {
    val byId = LinkedHashMap<String, T>()
    for (entity in a + b) {
        val existing = byId[entity.id]
        if (existing == null || newer(entity, existing)) byId[entity.id] = entity
    }
    return byId.values.sortedBy { it.id }
}

private fun newer(candidate: Stamped, current: Stamped): Boolean {
    val order = compareStamps(candidate.at, candidate.by, current.at, current.by)
    if (order != 0) return order > 0
    // Same stamp should mean the same change; prefer the tombstone so a delete is never lost.
    return candidate.deleted && !current.deleted
}

fun compareStamps(atA: Long, byA: String, atB: Long, byB: String): Int =
    compareValuesBy(atA to byA, atB to byB, { it.first }, { it.second })

/** Drops tombstones older than [maxAgeMillis]; a stale offline copy may then resurrect them. */
fun <T : Stamped> List<T>.prunedTombstones(now: Long, maxAgeMillis: Long = 90L * 24 * 60 * 60 * 1000): List<T> =
    filterNot { it.deleted && it.at < now - maxAgeMillis }

private const val ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

/** A random id for a new space, item, section and the like. */
fun newId(random: kotlin.random.Random = kotlin.random.Random.Default): String = buildString {
    repeat(12) { append(ID_ALPHABET[random.nextInt(ID_ALPHABET.length)]) }
}
