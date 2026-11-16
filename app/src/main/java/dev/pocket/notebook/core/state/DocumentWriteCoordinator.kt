package dev.pocket.notebook.core.state

import dev.pocket.notebook.domain.SaveOutcome
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex

/**
 * Application-owned, sequential writes. Routes only enqueue; acknowledgement follows durable IO.
 * Deletion uses the same IO gate so a queued/in-flight edit cannot recreate a deleted document.
 */
class DocumentWriteCoordinator(
    private val save: suspend (String, String) -> SaveOutcome,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val debounceMillis: Long = 200,
) {
    private data class Edit(val id: String, val source: String, val revision: Long)

    private val lifecycleGuard = Any()
    private var lifecycleEpoch = 0L

    fun foreground() {
        synchronized(lifecycleGuard) { lifecycleEpoch++ }
    }

    fun flushAndLock(lock: () -> Unit, onLocked: () -> Unit) {
        val epoch = synchronized(lifecycleGuard) { ++lifecycleEpoch }
        afterFlush {
            val locked =
                synchronized(lifecycleGuard) {
                    if (lifecycleEpoch == epoch) {
                        lock()
                        true
                    } else false
                }
            if (locked) withContext(Dispatchers.Main) { onLocked() }
        }
    }

    private val guard = Any()
    private val pending = linkedMapOf<String, Edit>()
    private val latest = mutableMapOf<String, Edit>()
    private val states = mutableMapOf<String, MutableStateFlow<DocumentSaveState>>()
    private val deleted = mutableSetOf<String>()
    // Used by saves AND destructive storage mutations. Never hold `guard` across IO.
    private val storageGate = Mutex()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val immediate = AtomicBoolean(false)
    private var sequence = 0L

    private suspend fun <T> withStorageGate(block: suspend () -> T): T {
        storageGate.lock()
        try {
            return block()
        } finally {
            storageGate.unlock()
        }
    }

    init {
        scope.launch {
            for (ignored in signal) {
                if (!immediate.getAndSet(false)) {
                    // Bounded coalescing window: never postpone persistence indefinitely
                    // while the user keeps typing. Wake immediately on requestFlush().
                    withTimeoutOrNull(debounceMillis) {
                        do {
                            signal.receive()
                        } while (!immediate.getAndSet(false))
                    }
                }
                val batch = synchronized(guard) { pending.values.toList().also { pending.clear() } }
                for (edit in batch) {
                    withStorageGate {
                        // A delete may have completed while this edit was awaiting the gate.
                        if (synchronized(guard) { edit.id in deleted }) return@withStorageGate
                        val result =
                            try {
                                Result.success(save(edit.id, edit.source))
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (error: Exception) {
                                Result.failure(error)
                            }
                        synchronized(guard) {
                            if (latest[edit.id]?.revision == edit.revision) {
                                if (result.isSuccess) latest.remove(edit.id)
                                mutableStatus(edit.id).value =
                                    result.fold(
                                        { DocumentSaveState.Saved(edit.revision, it) },
                                        { DocumentSaveState.Failed(edit.revision) },
                                    )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun mutableStatus(id: String) =
        states.getOrPut(id) { MutableStateFlow(DocumentSaveState.Idle) }

    fun status(id: String): StateFlow<DocumentSaveState> =
        synchronized(guard) { mutableStatus(id).asStateFlow() }

    fun submit(id: String, source: String): Long =
        synchronized(guard) {
            check(scope.isActive) { "Writer is closed" }
            val revision = ++sequence
            if (id in deleted) {
                // The route may still dispatch a final CodeMirror event while navigation exits.
                // Do not retain its text or schedule writes to the deleted UUID.
                mutableStatus(id).value = DocumentSaveState.Discarded(revision)
            } else {
                val edit = Edit(id, source, revision)
                latest[id] = edit
                pending[id] = edit
                mutableStatus(id).value = DocumentSaveState.Saving(revision)
                signal.trySend(Unit)
            }
            revision
        }

    fun pendingSource(id: String): String? = synchronized(guard) { latest[id]?.source }

    /** Authenticate/read storage even when a newer failed write is still held in process memory. */
    suspend fun loadLatest(
        id: String,
        load: suspend () -> dev.pocket.notebook.domain.DocumentSnapshot,
    ): dev.pocket.notebook.domain.DocumentSnapshot {
        flush()
        val persisted = load()
        return pendingSource(id)?.let { persisted.copy(source = it) } ?: persisted
    }

    fun retry(id: String) {
        synchronized(guard) { latest[id]?.takeUnless { id in deleted } }?.let {
            submit(id, it.source)
        }
    }

    /**
     * Serialize destructive operations with the actual save IO, not just the debounce queue.
     * A failed delete leaves edits untouched and eligible for retry. A successful delete
     * discards pending/failed plaintext and prevents stale WebViews from resubmitting the UUID.
     * `ids` must include every notebook below any folder being removed.
     */
    suspend fun <T> discardAndRun(ids: Set<String>, operation: suspend () -> T): T =
        withStorageGate {
            val result = operation()
            synchronized(guard) {
                deleted.addAll(ids)
                ids.forEach { id ->
                    pending.remove(id)
                    val edit = latest.remove(id)
                    states.remove(id)?.let { flow ->
                        flow.value =
                            DocumentSaveState.Discarded(
                                maxOf(flow.value.revision, edit?.revision ?: 0L),
                            )
                    }
                }
            }
            result
        }

    fun requestFlush() {
        immediate.set(true)
        signal.trySend(Unit)
    }

    suspend fun flush(): Boolean {
        val targets = synchronized(guard) { latest.mapValues { it.value.revision } }
        requestFlush()
        var successful = true
        for ((id, revision) in targets) {
            val flow = synchronized(guard) { if (id in deleted) null else mutableStatus(id).asStateFlow() }
            if (flow != null) {
                val result = flow.first {
                    (it.revision >= revision && it !is DocumentSaveState.Saving) ||
                        synchronized(guard) { id in deleted }
                }
                if (result is DocumentSaveState.Failed) successful = false
            }
        }
        return successful
    }

    fun afterFlush(action: suspend () -> Unit) {
        scope.launch {
            flush()
            action()
        }
    }

    fun close() {
        signal.close()
        scope.cancel()
    }
}
