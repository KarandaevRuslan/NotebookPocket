package dev.pocket.notebook.feature.workspace.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.pocket.notebook.R
import dev.pocket.notebook.feature.files.LocalPocketTools
import dev.pocket.notebook.i18n.AppStrings

/** All three modes share the same control and remain available on wide layouts. */
@Composable
internal fun WorkspaceModePicker(
    mode: Int,
    onModeChange: (Int) -> Unit,
    strings: AppStrings,
    compact: Boolean,
) {
    val labels =
        listOf(
            1 to (if (compact) strings.previewCompact else strings.preview),
            0 to strings.code,
            2 to LocalPocketTools.current.text(R.string.fs_resources),
        )
    val icons = listOf(Icons.Default.Visibility, Icons.Default.Code, Icons.Default.FolderOpen)
    val iconsOnly = compact && LocalDensity.current.fontScale >= 1.6f
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("workspace-mode-picker")) {
        labels.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onModeChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                modifier =
                    Modifier.testTag("workspace-mode-$value").semantics {
                        contentDescription = label
                    },
                icon = {},
                label = {
                    if (iconsOnly) Icon(icons[index], null)
                    else Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }
}
