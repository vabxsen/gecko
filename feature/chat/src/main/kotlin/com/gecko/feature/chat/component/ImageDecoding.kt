package com.gecko.feature.chat.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Base64-decodes and downsamples an attachment/generated image off the main thread. Chat
 * messages can carry many images across a long conversation, each potentially megapixels in
 * source resolution — decoding at full resolution directly in a composable body (as opposed to
 * here) would block the UI thread and re-run on every scroll-back-into-view inside a
 * [androidx.compose.foundation.lazy.LazyColumn]. [maxDimensionPx] bounds the decoded bitmap to
 * roughly what this call site actually displays, so memory scales with the visible UI rather
 * than the stored image's original size.
 */
@Composable
internal fun rememberDecodedBitmap(base64: String, maxDimensionPx: Int): Bitmap? {
    val state = produceState<Bitmap?>(initialValue = null, base64, maxDimensionPx) {
        value = withContext(Dispatchers.Default) { decodeSampledBitmap(base64, maxDimensionPx) }
    }
    return state.value
}

private fun decodeSampledBitmap(base64: String, maxDimensionPx: Int): Bitmap? = runCatching {
    val bytes = Base64.decode(base64, Base64.NO_WRAP)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimensionPx)
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}.getOrNull()

/** Largest power-of-two downsample that still leaves the image at least [maxDimensionPx] on its
 * longer side, matching [BitmapFactory.Options.inSampleSize]'s power-of-two requirement. */
private fun sampleSizeFor(width: Int, height: Int, maxDimensionPx: Int): Int {
    if (maxDimensionPx <= 0 || width <= 0 || height <= 0) return 1
    var sampleSize = 1
    while (maxOf(width, height) / (sampleSize * 2) >= maxDimensionPx) {
        sampleSize *= 2
    }
    return sampleSize
}
