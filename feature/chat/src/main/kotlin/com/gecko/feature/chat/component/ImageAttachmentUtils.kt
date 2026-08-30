package com.gecko.feature.chat.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_DIMENSION_PX = 1024
private const val JPEG_QUALITY = 80

/** Downsamples and JPEG-encodes an image URI to a base64 string suitable for local storage. */
suspend fun encodeImageAttachment(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION_PX)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return@runCatching null

        val scaled = downscaleIfNeeded(bitmap)
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}

private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sampleSize = 1
    var w = width
    var h = height
    while (w / 2 >= maxDimension || h / 2 >= maxDimension) {
        w /= 2
        h /= 2
        sampleSize *= 2
    }
    return sampleSize
}

private fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
    val largestSide = maxOf(bitmap.width, bitmap.height)
    if (largestSide <= MAX_DIMENSION_PX) return bitmap
    val scale = MAX_DIMENSION_PX.toFloat() / largestSide
    return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
}
