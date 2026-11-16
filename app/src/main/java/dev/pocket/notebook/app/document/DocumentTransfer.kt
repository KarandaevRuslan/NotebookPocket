package dev.pocket.notebook.app.document

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable

/** Android document picker concerns stay out of the application navigation and data layers. */
class DocumentTransfer
internal constructor(
    val import: () -> Unit,
    val export: (id: String, suggestedName: String) -> Unit,
    val pickImage: (id: String) -> Unit,
)

@Composable
fun rememberDocumentTransfer(
    onImport: (Uri) -> Unit,
    onExport: (String, Uri) -> Unit,
    onImage: (String, Uri) -> Unit,
    onPickerFinished: () -> Unit = {},
): DocumentTransfer {
    var pendingExport by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingImage by rememberSaveable { mutableStateOf<String?>(null) }
    val finished by androidx.compose.runtime.rememberUpdatedState(onPickerFinished)
    val imageCallback by androidx.compose.runtime.rememberUpdatedState(onImage)
    val importCallback by androidx.compose.runtime.rememberUpdatedState(onImport)
    val exportCallback by androidx.compose.runtime.rememberUpdatedState(onExport)

    val importPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            finished()
            if (uri != null) importCallback(uri)
        }
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            finished()
            val id = pendingImage
            pendingImage = null
            if (id != null && uri != null) imageCallback(id, uri)
        }
    val exportPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            finished()
            val id = pendingExport
            pendingExport = null
            if (uri != null && id != null) exportCallback(id, uri)
        }
    return remember(importPicker, exportPicker, imagePicker) {
        DocumentTransfer(
            import = {
                importPicker.launch(
                    arrayOf(
                        "text/html",
                        "text/plain",
                        "application/zip",
                        "application/octet-stream",
                    )
                )
            },
            export = { id, filename ->
                pendingExport = id
                exportPicker.launch("$filename.npkg")
            },
            pickImage = { id ->
                pendingImage = id
                imagePicker.launch(arrayOf("image/png", "image/jpeg", "image/webp"))
            },
        )
    }
}
