package eu.studiodeanna.openpantrymanager.store

class MemoryFileStore : FileStore {
    val files = mutableMapOf<String, String>()
    override suspend fun read(name: String): String? = files[name]
    override suspend fun write(name: String, content: String) {
        files[name] = content
    }
    override suspend fun delete(name: String) {
        files.remove(name)
    }
}
