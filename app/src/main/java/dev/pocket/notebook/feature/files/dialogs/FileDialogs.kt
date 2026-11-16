@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package dev.pocket.notebook.feature.files.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.pocket.notebook.R
import dev.pocket.notebook.data.*
import dev.pocket.notebook.feature.files.LocalPocketTools

@Composable
internal fun FileForm(
    title: String,
    dismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalPocketTools.current
    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(dismiss) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    t.text(R.string.fs_cancel),
                                )
                            }
                        },
                    )
                }
            ) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
internal fun FileNameForm(
    title: String,
    initial: String = "",
    dismiss: () -> Unit,
    submit: (String) -> Unit,
) {
    val t = LocalPocketTools.current
    var name by remember(initial) { mutableStateOf(initial) }
    FileForm(title, dismiss) {
        OutlinedTextField(
            name,
            { name = it.take(1024) },
            label = { Text(t.text(R.string.fs_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("file-name"),
        )
        Button(
            { submit(name.trim()) },
            enabled = name.isNotBlank(),
            modifier = Modifier.testTag("file-name-save"),
        ) {
            Text(t.text(R.string.fs_save))
        }
    }
}
