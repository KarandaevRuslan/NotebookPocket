import dev.pocket.notebook.data.NotebookOrder

fun main() {
    check(NotebookOrder.moved(listOf("a", "b", "c"), "a", "c") == listOf("b", "c", "a"))
    check(NotebookOrder.moved(listOf("a", "b", "c"), "c", "a") == listOf("c", "a", "b"))
    check(NotebookOrder.moved(listOf("a", "b", "c"), "b", "b") == listOf("a", "b", "c"))
    check(NotebookOrder.moved(listOf("a", "b", "c"), "z", "b") == listOf("a", "b", "c"))
    println("NotebookOrder: all 4 assertions passed")
}
