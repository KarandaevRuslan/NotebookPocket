import dev.pocket.notebook.data.NotebookMetadata
import dev.pocket.notebook.data.NotebookMetadataCodec

fun main() {
    val previous = NotebookMetadata("Блины", "Описание", "Автор", "1.0")
    check(NotebookMetadataCodec.forDraft("", previous) == previous)
    check(NotebookMetadataCodec.forDraft("<notebook><title></title>", previous) == previous)
    check(NotebookMetadataCodec.forDraft("<notebook><title>Новый</title></notebook>", previous).title == "Новый")
    check(NotebookMetadataCodec.forDraft("<notebook><title>${"x".repeat(121)}</title></notebook>", previous) == previous)
    println("Editor draft metadata recovery: 4 tests passed")
}
