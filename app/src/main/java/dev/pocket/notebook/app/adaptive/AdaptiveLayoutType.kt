package dev.pocket.notebook.app.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Adapted from the adaptive layout policy in the supplied MobileApp. */
enum class AdaptiveLayoutType {
    SinglePane,
    TwoPane,
}

@Composable
fun rememberAdaptiveLayoutType(maxWidth: Dp, maxHeight: Dp): AdaptiveLayoutType =
    remember(maxWidth, maxHeight) {
        if (maxWidth > maxHeight || maxWidth >= 600.dp) AdaptiveLayoutType.TwoPane
        else AdaptiveLayoutType.SinglePane
    }
