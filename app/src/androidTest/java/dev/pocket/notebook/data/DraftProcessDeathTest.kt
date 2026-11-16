package dev.pocket.notebook.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pocket.notebook.data.local.NotebookDatabase
import dev.pocket.notebook.domain.SaveOutcome
import java.io.File
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * CI runs the two methods in separate instrumentation invocations, with am force-stop between them.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DraftProcessDeathTest {
    private val context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private val databaseName = "process-death-regression.db"
    private val checkpoint = "<notebook><title>PROCESS_DEATH_DRAFT"
    private val valid =
        "<notebook><title>Recovered</title><script id=\"1\" type=\"module\">display(1)</script></notebook>"

    @Test
    fun a_checkpointBeforeKill() {
        context.deleteDatabase(databaseName)
        val db = Room.databaseBuilder(context, NotebookDatabase::class.java, databaseName).build()
        try {
            val repo = NotebookRepository(context, db)
            val parent = repo.createCollection("Recovery fixture", null).id
            val input = File(context.cacheDir, "process-fixture.html").apply { writeText(valid) }
            val id = repo.importDocument(Uri.fromFile(input), parent).id
            repo.putResource(id, "data.txt", "retained resource".toByteArray())
            repo.setPassword(parent, "process death regression password".toCharArray())
            assertEquals(SaveOutcome.DRAFT, repo.save(id, checkpoint))
            check(
                context
                    .getSharedPreferences("process-test", 0)
                    .edit()
                    .putString("id", id)
                    .putString("parent", parent)
                    .commit()
            )
            repo.lockAll()
        } finally {
            db.close()
        }
    }

    @Test
    fun b_restoreAfterKill() {
        val preferences = context.getSharedPreferences("process-test", 0)
        val id = requireNotNull(preferences.getString("id", null))
        val parent = requireNotNull(preferences.getString("parent", null))
        val db = Room.databaseBuilder(context, NotebookDatabase::class.java, databaseName).build()
        try {
            val repo = NotebookRepository(context, db)
            assertThrows(NotebookLockedException::class.java) { repo.read(id) }
            repo.unlock(parent, "process death regression password".toCharArray())
            assertEquals(checkpoint, repo.read(id))
            assertEquals(
                "retained resource",
                repo.readResource(id, "data.txt").toString(Charsets.UTF_8),
            )
            assertEquals(SaveOutcome.COMMITTED, repo.save(id, valid))
            assertFalse(repo.load(id).fromDraft)
            repo.deleteItems(listOf(parent))
            repo.lockAll()
            preferences.edit().clear().commit()
        } finally {
            db.close()
            context.deleteDatabase(databaseName)
        }
    }
}
