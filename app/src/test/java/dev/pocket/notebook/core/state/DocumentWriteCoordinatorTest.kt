package dev.pocket.notebook.core.state

import dev.pocket.notebook.domain.SaveOutcome
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DocumentWriteCoordinatorTest {
    @Test
    fun largeRapidEditsAreCoalescedAndNeverBlockTheSubmittingThread() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val count = AtomicInteger()
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        var last = ""
        val writer =
            DocumentWriteCoordinator(
                { _, source ->
                    maximum.updateAndGet { maxOf(it, active.incrementAndGet()) }
                    count.incrementAndGet()
                    started.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    last = source
                    active.decrementAndGet()
                    SaveOutcome.COMMITTED
                },
                debounceMillis = 10,
            )
        try {
            writer.submit("id", "first")
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val large = "x".repeat(1024 * 1024)
            repeat(100) { writer.submit("id", large + it) }
            assertTrue(writer.status("id").value is DocumentSaveState.Saving)
            release.countDown()
            assertTrue(withTimeout(10000) { writer.flush() })
            assertEquals(large + 99, last)
            assertEquals(1, maximum.get())
            assertTrue(count.get() <= 2)
        } finally {
            release.countDown()
            writer.close()
        }
    }

    @Test
    fun failureStaysFailedUntilAnExplicitSuccessfulRetry() = runBlocking {
        var failing = true
        val writer =
            DocumentWriteCoordinator(
                { _, _ -> if (failing) throw IOException("disk full") else SaveOutcome.DRAFT },
                debounceMillis = 0,
            )
        try {
            writer.submit("id", "<notebook><tit")
            assertFalse(withTimeout(5000) { writer.flush() })
            assertTrue(writer.status("id").value is DocumentSaveState.Failed)
            failing = false
            writer.retry("id")
            assertTrue(withTimeout(5000) { writer.flush() })
            assertEquals(
                SaveOutcome.DRAFT,
                (writer.status("id").value as DocumentSaveState.Saved).outcome,
            )
        } finally {
            writer.close()
        }
    }

    @Test
    fun reopeningAfterFailedIoKeepsThePendingTextAndStillRequiresReadAuthorization(): Unit =
        runBlocking {
            val writer =
                DocumentWriteCoordinator(
                    { _, _ -> throw IOException("disk full") },
                    debounceMillis = 0,
                )
            try {
                writer.submit("id", "new unsaved text")
                assertFalse(withTimeout(5000) { writer.flush() })
                val restored =
                    writer.loadLatest("id") {
                        dev.pocket.notebook.domain.DocumentSnapshot("old durable text", false)
                    }
                assertEquals("new unsaved text", restored.source)
                assertTrue(writer.status("id").value is DocumentSaveState.Failed)
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { writer.loadLatest("id") { error("document is locked") } }
                }
            } finally {
                writer.close()
            }
        }

    @Test
    fun deleteDiscardsPendingTextAndPreventsLateWebViewWrites() = runBlocking {
        val written = mutableListOf<String>()
        val deleting = CompletableDeferred<Unit>()
        val resumeDelete = CompletableDeferred<Unit>()
        val writer =
            DocumentWriteCoordinator(
                { id, _ ->
                    synchronized(written) { written.add(id) }
                    SaveOutcome.COMMITTED
                },
                debounceMillis = 0,
            )
        try {
            val deletion = async(Dispatchers.IO) {
                writer.discardAndRun(setOf("nested")) {
                    deleting.complete(Unit)
                    resumeDelete.await()
                }
            }
            withTimeout(5000) { deleting.await() }
            writer.submit("nested", "text while folder is being deleted")
            resumeDelete.complete(Unit)
            withTimeout(5000) { deletion.await() }
            assertNull(writer.pendingSource("nested"))
            assertTrue(withTimeout(5000) { writer.flush() })
            writer.submit("nested", "late editor callback")
            assertNull(writer.pendingSource("nested"))
            assertTrue(writer.status("nested").value is DocumentSaveState.Discarded)
            assertTrue(withTimeout(5000) { writer.flush() })
            assertTrue(synchronized(written) { written.isEmpty() })
        } finally {
            resumeDelete.complete(Unit)
            writer.close()
        }
    }

    @Test
    fun deleteWaitsForInFlightSaveBeforeRemovingDocument() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val writer =
            DocumentWriteCoordinator(
                { _, _ ->
                    started.complete(Unit)
                    release.await()
                    synchronized(events) { events.add("write") }
                    SaveOutcome.COMMITTED
                },
                debounceMillis = 0,
            )
        try {
            writer.submit("id", "before deletion")
            withTimeout(5000) { started.await() }
            val deletion = async(Dispatchers.IO) {
                writer.discardAndRun(setOf("id")) {
                    synchronized(events) { events.add("delete") }
                }
            }
            release.complete(Unit)
            withTimeout(5000) { deletion.await() }
            assertEquals(listOf("write", "delete"), synchronized(events) { events.toList() })
            writer.submit("id", "after deletion")
            assertTrue(withTimeout(5000) { writer.flush() })
            assertEquals(2, synchronized(events) { events.size })
        } finally {
            release.complete(Unit)
            writer.close()
        }
    }

    @Test
    fun failedDeletionDoesNotDiscardUnsavedEdits() = runBlocking {
        val writer = DocumentWriteCoordinator(
            { _, _ -> throw IOException("disk full") },
            debounceMillis = 0,
        )
        try {
            writer.submit("id", "secret still needed if deletion fails")
            assertFalse(withTimeout(5000) { writer.flush() })
            assertThrows(IOException::class.java) {
                runBlocking {
                    writer.discardAndRun(setOf("id")) { throw IOException("delete failed") }
                }
            }
            assertEquals("secret still needed if deletion fails", writer.pendingSource("id"))
            assertTrue(writer.status("id").value is DocumentSaveState.Failed)
        } finally {
            writer.close()
        }
    }


    @Test
    fun urgentFlushWakesWriterAlreadyInsideDebounce() = runBlocking {
        val saved = CompletableDeferred<Unit>()
        val writer = DocumentWriteCoordinator(
            { _, _ -> saved.complete(Unit); SaveOutcome.COMMITTED },
            debounceMillis = 10_000,
        )
        try {
            writer.submit("id", "last edit before app pause")
            delay(50) // The worker has consumed the first signal and begun debouncing.
            writer.requestFlush()
            withTimeout(2000) { saved.await() }
            assertTrue(withTimeout(2000) { writer.flush() })
        } finally {
            writer.close()
        }
    }


    @Test
    fun continuousTypingStillProducesPeriodicCheckpoints() = runBlocking {
        val firstSaved = CompletableDeferred<Unit>()
        val writer = DocumentWriteCoordinator(
            { _, _ -> firstSaved.complete(Unit); SaveOutcome.COMMITTED },
            debounceMillis = 70,
        )
        val producer = launch {
            repeat(40) { i ->
                writer.submit("id", "edit-$i")
                delay(25)
            }
        }
        try {
            withTimeout(650) { firstSaved.await() }
            assertTrue("writer must save before typing stops", producer.isActive)
            producer.join()
            assertTrue(withTimeout(5000) { writer.flush() })
        } finally {
            producer.cancel()
            writer.close()
        }
    }

}
