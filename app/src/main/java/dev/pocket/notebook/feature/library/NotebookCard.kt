package dev.pocket.notebook.feature.library

import dev.pocket.notebook.feature.files.actions.LibraryFileMenu
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.core.ui.components.card.AppCard
import dev.pocket.notebook.core.ui.components.card.AppCardContent
import dev.pocket.notebook.core.ui.components.card.AppCardDefaults
import dev.pocket.notebook.core.ui.components.card.AppCardSwipeAction
import dev.pocket.notebook.core.ui.components.card.AppSwipeActionCard
import dev.pocket.notebook.data.NotebookEntry
import dev.pocket.notebook.i18n.AppStrings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reuses the supplied AppCard / AppSwipeActionCard. Only project actions are feature-specific. */
@Composable
internal fun NotebookCard(
  note: NotebookEntry,
  modifier: Modifier = Modifier,
  strings: AppStrings,
  locale: String,
  selected: Boolean,
  coverBytes: ByteArray?,
  onOpen: (String) -> Unit,
  onEditMetadata: (String) -> Unit,
  onMoveToFolder: (String) -> Unit,
  onExport: (String) -> Unit,
  onDelete: (NotebookEntry) -> Unit,
) {
  val updated =
    SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.forLanguageTag(locale))
      .format(Date(note.updated))
  var menu by remember { mutableStateOf(false) }
  val shape = AppCardDefaults.shape
  AppSwipeActionCard(
    modifier = modifier.fillMaxWidth(),
    shape = shape,
    startToEndAction =
      AppCardSwipeAction(
        label = strings.exportFile,
        icon = dev.pocket.notebook.core.ui.FileTransferIcons.Export,
        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      ),
    endToStartAction =
      AppCardSwipeAction(
        label = strings.delete,
        icon = Icons.Default.Delete,
        backgroundColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
      ),
    onStartToEnd = { onExport(note.id) },
    onEndToStart = { onDelete(note) },
  ) {
    AppCard(
      modifier = Modifier.fillMaxWidth(),
      shape = shape,
      colors =
        if (selected)
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else AppCardDefaults.colors(),
      border =
        if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        else AppCardDefaults.border(),
      onClick = { onOpen(note.id) },
    ) {
      AppCardContent(
        title = note.title.ifBlank { strings.untitled },
        subtitle = note.description.takeIf { it.isNotBlank() },
        supportingText =
          buildList {
              if (note.author.isNotBlank()) add(note.author)
              if (note.version.isNotBlank()) add("v${note.version}")
              add(updated)
            }
            .joinToString(" • "),
        supportingTextMaxLines = 3,
        leadingContent = { NotebookCover(coverBytes) },
        verticalAlignment = Alignment.CenterVertically,
        titleMaxLines = 2,
        trailingContent = {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (note.encrypted)
              Icon(
                Icons.Default.Lock,
                contentDescription = strings.encryptedLabel,
                tint = MaterialTheme.colorScheme.primary,
              )
            IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
              Icon(Icons.Default.MoreVert, contentDescription = strings.moreActions)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
              dev.pocket.notebook.feature.files.actions.LibraryFileMenu(note.id, null) { menu = false }
              if (note.unlocked)
                DropdownMenuItem(
                  text = { Text(strings.editMetadata) },
                  onClick = {
                    menu = false
                    onEditMetadata(note.id)
                  },
                  leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                )
              DropdownMenuItem(
                text = { Text(strings.moveToFolder) },
                onClick = {
                  menu = false
                  onMoveToFolder(note.id)
                },
                leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) },
              )
            }
          }
        },
      )
    }
  }
}
