package dev.pocket.notebook.core.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class FileTransferIconsTest {
  @Test
  fun importEntersAndExportLeavesTheApplication() {
    assertTrue(FileTransferIcons.Import.name.endsWith("FileDownload"))
    assertTrue(FileTransferIcons.Export.name.endsWith("FileUpload"))
  }
}
