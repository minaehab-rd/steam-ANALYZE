package com.strawlens.analyzer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.InputStream

object ImageUtils {

    /**
     * Loads a bitmap from a content Uri, corrects orientation using EXIF data
     * (camera photos are frequently stored sideways), and downscales it so the
     * longest side is at most [maxDimension] pixels — this keeps the upload small
     * and fast while staying sharp enough for the model to judge stems vs product.
     */
    fun loadAndPrepareBitmap(context: Context, uri: Uri, maxDimension: Int = 1536): Bitmap? {
        val resolver = context.contentResolver

        // First pass: read bounds only, to compute a safe inSampleSize.
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { input: InputStream ->
            BitmapFactory.decodeStream(input, null, boundsOptions)
        }

        var sampleSize = 1
        val (w, h) = boundsOptions.outWidth to boundsOptions.outHeight
        if (w > 0 && h > 0) {
            val largestSide = maxOf(w, h)
            while (largestSide / sampleSize > maxDimension * 2) {
                sampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val rawBitmap = resolver.openInputStream(uri)?.use { input: InputStream ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: return null

        val orientedBitmap = applyExifRotation(context, uri, rawBitmap)
        return scaleDownIfNeeded(orientedBitmap, maxDimension)
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val rotationDegrees = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largestSide = maxOf(bitmap.width, bitmap.height)
        if (largestSide <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largestSide
        val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /** Encodes a bitmap as base64 JPEG, ready to send as inlineData to Gemini. */
    fun bitmapToBase64Jpeg(bitmap: Bitmap, quality: Int = 90): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
    }
}
