package dev.pocket.notebook.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload

/** Import enters the app; export leaves the app. */
object FileTransferIcons {
  val Import
    get() = Icons.Default.FileDownload

  val Export
    get() = Icons.Default.FileUpload
}
