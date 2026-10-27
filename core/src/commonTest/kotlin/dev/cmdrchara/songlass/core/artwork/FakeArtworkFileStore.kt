package dev.cmdrchara.songlass.core.artwork

/**
 * In-memory [ArtworkFileStore] for tests: no filesystem, no clock, no Android.
 *
 * [failWrites] simulates a full disk or a refused write; [deleted] records every delete attempt so
 * eviction and removal can be asserted from the outside.
 */
internal class FakeArtworkFileStore(
    private val files: MutableMap<String, ByteArray> = mutableMapOf(),
    private val modified: MutableMap<String, Long> = mutableMapOf(),
    var failWrites: Boolean = false,
    var failReads: Boolean = false,
) : ArtworkFileStore {
    val deleted = mutableListOf<String>()

    override suspend fun list(): List<StoredArtworkFile> = files.map { (name, bytes) ->
        StoredArtworkFile(name, bytes.size.toLong(), modified[name] ?: 0L)
    }

    override suspend fun read(name: String): ByteArray? {
        if (failReads) error("read refused")
        return files[name]?.copyOf()
    }

    override suspend fun write(name: String, bytes: ByteArray): Boolean {
        if (failWrites) return false
        files[name] = bytes.copyOf()
        modified[name] = (modified[name] ?: 0L) + 1
        return true
    }

    override suspend fun delete(name: String): Boolean {
        deleted += name
        modified.remove(name)
        return files.remove(name) != null
    }

    fun names(): Set<String> = files.keys.toSet()
    fun contains(name: String): Boolean = files.containsKey(name)
    fun sizeOf(name: String): Int? = files[name]?.size
    fun putRaw(name: String, bytes: ByteArray) {
        files[name] = bytes.copyOf()
    }
    fun removeRaw(name: String) {
        files.remove(name)
    }
}
