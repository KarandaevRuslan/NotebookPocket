package dev.pocket.notebook.feature.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

/** Downsample images from disk, never decode full-resolution untrusted images into UI memory. */
@Composable
fun NotebookCover(bytes: ByteArray?, modifier: Modifier = Modifier) {
    val bitmap = remember(bytes) {
        bytes?.let { data -> runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            require(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192)
            val sample = generateSequence(1) { it * 2 }
                .first { bounds.outWidth / it <= 320 && bounds.outHeight / it <= 320 }
            BitmapFactory.decodeByteArray(data, 0, data.size,
                BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull() }
    }
    Box(modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop)
        else Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}
