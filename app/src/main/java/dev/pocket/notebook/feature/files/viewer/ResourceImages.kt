@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.pocket.notebook.feature.files.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.io.ByteArrayInputStream
import kotlinx.coroutines.*

internal fun resourceImagePath(path: String) =
    path.substringAfterLast('.').lowercase() in
        setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif", "avif", "svg", "ico")

internal data class ResourceImage(val bitmap: Bitmap, val width: Int, val height: Int)

internal fun decodeResourceImage(bytes: ByteArray): ResourceImage? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options =
        BitmapFactory.Options().apply {
            inSampleSize = 1
            while (
                bounds.outWidth / inSampleSize > 2048 || bounds.outHeight / inSampleSize > 2048
            ) inSampleSize *= 2
        }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    val orientation =
        runCatching {
                ExifInterface(ByteArrayInputStream(bytes))
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
            }
            .getOrDefault(1)
    val matrix =
        Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> {
                    setRotate(90f)
                    postScale(-1f, 1f)
                }
                6 -> setRotate(90f)
                7 -> {
                    setRotate(-90f)
                    postScale(-1f, 1f)
                }
                8 -> setRotate(270f)
            }
        }
    val oriented =
        if (orientation in 2..8)
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        else bitmap
    if (oriented !== bitmap) bitmap.recycle()
    return ResourceImage(
        oriented,
        if (orientation in 5..8) bounds.outHeight else bounds.outWidth,
        if (orientation in 5..8) bounds.outWidth else bounds.outHeight,
    )
}
