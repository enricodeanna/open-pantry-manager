package eu.studiodeanna.openpantrymanager.store

/** Flat, name-addressed storage for the app's own files on this device. */
interface FileStore {
    suspend fun read(name: String): String?
    suspend fun write(name: String, content: String)
    suspend fun delete(name: String)
}
