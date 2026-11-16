import dev.pocket.notebook.data.NotebookMetadata
import dev.pocket.notebook.data.NotebookMetadataCodec

fun main() {
    val html = """<!doctype html><notebook theme="air"><title>Existing</title><script id="1" type="module">display(1 + 2)</script></notebook>"""
    val modified = NotebookMetadataCodec.update(html, NotebookMetadata("One & Two <3", "Details \"quoted\" & <ok>"))
    check(NotebookMetadataCodec.read(modified) == NotebookMetadata("One & Two <3", "Details \"quoted\" & <ok>"))
    check(modified.contains("<script id=\"1\" type=\"module\">display(1 + 2)</script>"))
    check(NotebookMetadataCodec.update(modified, NotebookMetadata("A", "B")).contains("<title>A</title>"))
    val missingTitle = "<notebook><script id=\"1\" type=\"module\">display(1)</script></notebook>"
    check(NotebookMetadataCodec.read(NotebookMetadataCodec.update(missingTitle, NotebookMetadata("Test", "Desc"))).title == "Test")
    println("NotebookMetadataCodec: all 5 assertions passed")
}
