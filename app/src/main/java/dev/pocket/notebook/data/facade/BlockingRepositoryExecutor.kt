package dev.pocket.notebook.data.facade

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** One shared monitor keeps collection caches and multi-file mutations serialized across ports. */
internal class BlockingRepositoryExecutor {
    @Synchronized
    fun <T> execute(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }
}
