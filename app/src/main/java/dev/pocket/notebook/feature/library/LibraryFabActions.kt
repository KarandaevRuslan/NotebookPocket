package dev.pocket.notebook.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.pocket.notebook.i18n.AppStrings

/**
 * Standard Material 3 FABs. The primary '+' responds to tap (new document) or long press
 * (expand/collapse). In landscape, the layout is ALWAYS horizontal.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryFabActions(
  strings: AppStrings,
  onCreate: () -> Unit,
  onImport: () -> Unit,
  onCreateFolder: () -> Unit,
  landscape: Boolean,
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
  allowFolders: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val buttons: @Composable () -> Unit = {
    if (expanded) {
      if (allowFolders)
        FloatingActionButton(
          onClick = onCreateFolder,
          modifier = Modifier.size(56.dp),
          shape = RoundedCornerShape(16.dp),
          containerColor = MaterialTheme.colorScheme.tertiaryContainer,
          contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
          Icon(Icons.Default.CreateNewFolder, contentDescription = strings.newFolder)
        }
      FloatingActionButton(
        onClick = onImport,
        modifier = Modifier.size(56.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      ) {
        Icon(
          dev.pocket.notebook.core.ui.FileTransferIcons.Import,
          contentDescription = strings.importFile,
        )
      }
    }
    // Non-clickable Material Surface to avoid nested clickable detectors.
    // Compose Foundation owns both click and long-click semantics here.
    Surface(
      modifier =
        Modifier.size(56.dp)
          .combinedClickable(
            role = Role.Button,
            onClickLabel = strings.newNotebook,
            onLongClickLabel = if (expanded) strings.collapseActions else strings.expandActions,
            onClick = onCreate,
            onLongClick = onToggleExpanded,
          ),
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      shadowElevation = 6.dp,
    ) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Add, contentDescription = strings.newNotebook)
      }
    }
  }
  if (landscape)
    Row(
      modifier,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      buttons()
    }
  else
    Column(
      modifier,
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.End,
    ) {
      buttons()
    }
}
