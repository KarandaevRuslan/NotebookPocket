package dev.pocket.notebook.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NotebookOrderTest {
    @Test fun topItemCanMoveDownInTwoItemFolder() {
        assertEquals(listOf("B", "A"), NotebookOrder.moved(listOf("A", "B"), "A", "B"))
    }

    @Test fun bottomItemCanMoveUpInTwoItemFolder() {
        assertEquals(listOf("B", "A"), NotebookOrder.moved(listOf("A", "B"), "B", "A"))
    }

    @Test fun folderMovesExactlyOneCellInEitherDirection() {
        val original = listOf("Doc1", "Doc2", "Doc3", "Folder")
        val movedOne = NotebookOrder.moved(original, "Folder", "Doc3")
        assertEquals(listOf("Doc1", "Doc2", "Folder", "Doc3"), movedOne)
        assertEquals(original, NotebookOrder.moved(movedOne, "Folder", "Doc3"))
    }

    @Test fun reorderSameItemAndInvalidIndexAreNoops() {
        val ids = listOf("a", "b")
        assertEquals(ids, NotebookOrder.moved(ids, "a", "a"))
        assertEquals(ids, NotebookOrder.movedIndices(ids, -1, 1))
        assertEquals(ids, NotebookOrder.movedIndices(ids, 0, 5))
    }
}
