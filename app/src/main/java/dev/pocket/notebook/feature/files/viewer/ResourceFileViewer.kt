@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.pocket.notebook.feature.files.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import dev.pocket.notebook.R
import dev.pocket.notebook.data.ResourceText
import dev.pocket.notebook.data.security.NotebookCrypto
import dev.pocket.notebook.feature.files.LocalPocketTools
import dev.pocket.notebook.feature.files.resourceSize
import dev.pocket.notebook.feature.files.state.ResourceOpen
import kotlinx.coroutines.*

private data class OpenedResource(
    val bytes: ByteArray,
    val hash: String,
    val text: ResourceText?,
    val image: ResourceImage?,
    val svg: Boolean,
    val protected: Boolean,
)

@Composable
internal fun ResourceFileDialog(target: ResourceOpen, dismiss: () -> Unit) {
    val t = LocalPocketTools.current
    var loaded by remember(target) { mutableStateOf<OpenedResource?>(null) }
    var failure by remember(target) { mutableStateOf<String?>(null) }
    var forceText by remember(target) { mutableStateOf(target.edit) }
    LaunchedEffect(target) {
        try {
            loaded =
                withContext(Dispatchers.IO) {
                    val bytes = t.repo.readResource(target.owner, target.path)
                    val text = ResourceText.decode(bytes)
                    val svg = target.path.endsWith(".svg", true) && text != null
                    OpenedResource(
                        bytes,
                        NotebookCrypto.sha256(bytes),
                        text,
                        if (svg) null else runCatching { decodeResourceImage(bytes) }.getOrNull(),
                        svg,
                        t.repo.ownerProtected(target.owner),
                    )
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = t.errorText(e)
        }
    }
    val file = loaded
    if (file != null && file.text != null && (forceText || (!file.svg && file.image == null))) {
        ResourceTextEditor(target, file.text, file.hash, file.protected, dismiss)
        return
    }
    val secure =
        file?.protected ?: runCatching { t.repo.ownerProtected(target.owner) }.getOrDefault(true)
    Dialog(
        dismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                securePolicy = if (secure) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit,
            ),
    ) {
        var scale by remember(target) { mutableFloatStateOf(1f) }
        var x by remember(target) { mutableFloatStateOf(0f) }
        var y by remember(target) { mutableFloatStateOf(0f) }
        var light by remember(target) { mutableStateOf(false) }
        var reset by remember(target) { mutableIntStateOf(0) }
        // Opaque edge-to-edge overlay also covers the underlying landscape rail.
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
                contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                topBar = {
                    TopAppBar(
                        windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                        title = { Text(target.path, maxLines = 1) },
                        navigationIcon = {
                            IconButton(dismiss) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    t.text(R.string.fs_cancel),
                                )
                            }
                        },
                        actions = {
                            if (file?.text != null)
                                IconButton({ forceText = true }) {
                                    Icon(Icons.Default.Edit, t.text(R.string.res_edit))
                                }
                            IconButton({ light = !light }) {
                                Icon(Icons.Default.Contrast, t.text(R.string.res_toggle_background))
                            }
                            IconButton({
                                scale = 1f
                                x = 0f
                                y = 0f
                                reset++
                            }) {
                                Icon(Icons.Default.RestartAlt, t.text(R.string.res_image_reset))
                            }
                        },
                    )
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    file?.let {
                        Text(
                            resourceSize(it.bytes.size, t.language) +
                                (it.image?.let { i -> " · ${i.width} × ${i.height}" } ?: ""),
                            Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Box(
                        Modifier.weight(1f)
                            .fillMaxWidth()
                            .clipToBounds()
                            .background(
                                if (light) Color(0xfff4f4f4)
                                else MaterialTheme.colorScheme.surfaceContainer
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            failure != null ->
                                Text(
                                    failure!!,
                                    Modifier.padding(24.dp),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            file == null -> CircularProgressIndicator()
                            forceText ->
                                Text(t.text(R.string.res_unsupported), Modifier.padding(24.dp))
                            file.svg -> key(reset) { ResourceSvgPreview(file.bytes) }
                            file.image != null ->
                                Image(
                                    file.image.bitmap.asImageBitmap(),
                                    target.path,
                                    Modifier.fillMaxSize()
                                        .testTag("resource-image-preview")
                                        .pointerInput(target) {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                scale = (scale * zoom).coerceIn(1f, 8f)
                                                x =
                                                    if (scale == 1f) 0f
                                                    else
                                                        (x + pan.x).coerceIn(
                                                            -size.width * scale,
                                                            size.width * scale,
                                                        )
                                                y =
                                                    if (scale == 1f) 0f
                                                    else
                                                        (y + pan.y).coerceIn(
                                                            -size.height * scale,
                                                            size.height * scale,
                                                        )
                                            }
                                        }
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            translationX = x
                                            translationY = y
                                        },
                                )
                            else -> Text(t.text(R.string.res_unsupported), Modifier.padding(24.dp))
                        }
                    }
                }
            }
        }
    }
}

/** SVG is an image, never a top-level document. No JS bridge, JS, file access or network. */
