@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.pocket.notebook.feature.files.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Keep both localized resource-scope labels inside the available portrait width. */
@Composable
internal fun ResourceScopeSelector(
    notebookLabel: String,
    sharedLabel: String,
    selectedShared: Boolean,
    onSelectNotebook: () -> Unit,
    onSelectShared: () -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().testTag("resource-scope-selector"),
    ) {
        listOf(notebookLabel, sharedLabel).forEachIndexed { index, label ->
            SegmentedButton(
                selected = (index == 1) == selectedShared,
                onClick = if (index == 0) onSelectNotebook else onSelectShared,
                shape = SegmentedButtonDefaults.itemShape(index, 2),
                modifier =
                    Modifier.weight(1f)
                        .heightIn(min = 56.dp)
                        .testTag(if (index == 0) "resource-scope-notebook" else "resource-scope-shared"),
                // The built-in check icon squeezes long labels on narrow phones.
                // Selection remains visible through the segmented button's active colors.
                icon = {},
                label = {
                    Text(
                        text = label,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                },
            )
        }
    }
}
