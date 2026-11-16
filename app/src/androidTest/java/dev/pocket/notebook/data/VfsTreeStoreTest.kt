package dev.pocket.notebook.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pocket.notebook.data.local.NotebookDatabase
import dev.pocket.notebook.data.local.VfsNodeEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VfsTreeStoreTest {
    @Test fun nestedFoldersSharedOrderingAndCycleSafety() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, NotebookDatabase::class.java).build()
        try {
            val dao = db.vfsDao()
            val store = VfsTreeStore(db, dao)
            val parent = store.createFolder("Parent", null)
            val child = store.createFolder("Child", parent.id)
            val first = VfsNodeEntity("file-1", parent.id, "NOTEBOOK", 1, "First")
            val second = VfsNodeEntity("file-2", parent.id, "NOTEBOOK", 2, "Second")
            dao.insert(first)
            dao.insert(second)
            store.reorder(second.id, child.id)
            assertEquals(listOf(second.id, child.id, first.id), store.children(parent.id).map { it.id })
            store.reparent(second.id, child.id)
            assertEquals(listOf(second.id), store.children(child.id).map { it.id })
            assertEquals(listOf(child.id, first.id), store.children(parent.id).map { it.id })
            val invalid = runCatching { store.reparent(parent.id, child.id) }
            assertFalse(invalid.isSuccess)
            val deleted = store.deleteRecursively(parent.id)
            assertEquals(setOf(first.id, second.id), deleted.toSet())
            assertEquals(0, dao.count())
        } finally { db.close() }
    }

    @Test fun twoItemReorderWorksBothWaysAndOrderIsFolderLocal() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, NotebookDatabase::class.java).build()
        try {
            val dao = db.vfsDao()
            val store = VfsTreeStore(db, dao)
            val f = store.createFolder("Section", null)
            dao.insert(VfsNodeEntity("a", f.id, "NOTEBOOK", 0, "A"))
            dao.insert(VfsNodeEntity("b", f.id, "NOTEBOOK", 1, "B"))
            store.reorder("a", "b")
            assertEquals(listOf("b", "a"), store.children(f.id).map { it.id })
            store.reorder("a", "b")
            assertEquals(listOf("a", "b"), store.children(f.id).map { it.id })
            // Root contents have a separate order; operations in Section don't modify it.
            assertEquals(listOf(f.id), store.children(null).map { it.id })
            assertFalse(runCatching { store.setOrder(f.id, listOf("a", "foreign")) }.isSuccess)
        } finally { db.close() }
    }

    @Test fun mixedFolderAndDocumentsMoveOnlyOnePosition() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, NotebookDatabase::class.java).build()
        try {
            val dao = db.vfsDao()
            val store = VfsTreeStore(db, dao)
            repeat(3) { i ->
                dao.insert(VfsNodeEntity("doc$i", null, "NOTEBOOK", i, "Doc $i"))
            }
            val folder = store.createFolder("Folder", null)
            store.setOrder(null, listOf("doc0", "doc1", "doc2", folder.id))
            store.reorder(folder.id, "doc2")
            assertEquals(listOf("doc0", "doc1", folder.id, "doc2"), store.children(null).map { it.id })
            store.reorder("doc2", folder.id)
            assertEquals(listOf("doc0", "doc1", "doc2", folder.id), store.children(null).map { it.id })
        } finally { db.close() }
    }
}
