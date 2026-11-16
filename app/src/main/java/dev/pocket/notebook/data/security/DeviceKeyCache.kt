package dev.pocket.notebook.data.security

import android.content.Context
import java.io.File

/** Session-only derived keys. Historical on-disk key caches are discarded on upgrade. */
class DeviceKeyCache(context: Context) {
    init {
        File(context.filesDir, "document-keys").deleteRecursively()
    }

    private val keys = mutableMapOf<String, ByteArray>()

    @Synchronized
    fun put(id: String, derivedKey: ByteArray) {
        require(derivedKey.size == 32)
        forget(id)
        keys[id] = derivedKey.copyOf()
    }

    @Synchronized fun get(id: String): ByteArray? = keys[id]?.copyOf()

    @Synchronized
    fun forget(id: String) {
        keys.remove(id)?.fill(0)
    }

    @Synchronized
    fun clear() {
        keys.values.forEach { it.fill(0) }
        keys.clear()
    }
}
