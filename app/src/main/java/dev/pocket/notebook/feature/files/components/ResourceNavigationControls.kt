@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.pocket.notebook.feature.files.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import dev.pocket.notebook.R
import dev.pocket.notebook.feature.files.LocalPocketTools
import dev.pocket.notebook.feature.files.state.ResourceNavigation

@Composable
internal fun ResourceAction(
    label: Int,
    icon: ImageVector,
    enabled: Boolean = true,
    tag: String? = null,
    onClick: () -> Unit,
) {
    val text = LocalPocketTools.current.text(label)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick,
            enabled = enabled,
            modifier = if (tag == null) Modifier else Modifier.testTag(tag),
        ) {
            Icon(icon, text)
        }
    }
}

@Composable
internal fun ResourceBreadcrumbs(nav: ResourceNavigation, onPath: () -> Unit) {
    val t = LocalPocketTools.current
    val scroll = rememberScrollState()
    LaunchedEffect(nav.path, scroll.maxValue) { scroll.animateScrollTo(scroll.maxValue) }
    Row(
        Modifier.fillMaxWidth().testTag("resource-navigation"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ResourceAction(R.string.res_back, Icons.AutoMirrored.Filled.ArrowBack, nav.canBack) {
            nav.back()
        }
        ResourceAction(
            R.string.res_forward,
            Icons.AutoMirrored.Filled.ArrowForward,
            nav.canForward,
        ) {
            nav.forward()
        }
        ResourceAction(R.string.res_up, Icons.Default.ArrowUpward, nav.path.isNotEmpty()) {
            nav.up()
        }
        Row(
            Modifier.weight(1f).horizontalScroll(scroll),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton({ nav.go("") }) { Text(t.text(R.string.fs_root)) }
            var current = ""
            nav.path
                .split('/')
                .filter { it.isNotEmpty() }
                .forEach { part ->
                    current = if (current.isEmpty()) part else "$current/$part"
                    val target = current
                    Text("›")
                    TextButton({ nav.go(target) }) { Text(part, maxLines = 1) }
                }
        }
        ResourceAction(
            R.string.res_go_path,
            Icons.Default.EditLocationAlt,
            tag = "resource-go-path",
            onClick = onPath,
        )
    }
}
