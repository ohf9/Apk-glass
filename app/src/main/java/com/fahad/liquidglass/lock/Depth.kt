package com.fahad.liquidglass.lock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * Depth effect: copy the chosen photo in, then find the main subject (person, pet,
 * object) on-device with ML Kit and save it as a transparent PNG. The lock screen
 * draws: wallpaper -> elements marked "behind" -> subject -> everything else.
 */
object Depth {
    /** Returns true if a subject was found. */
    suspend fun importWallpaper(context: Context, uri: Uri, store: LockStore): Boolean = withContext(Dispatchers.IO) {
        val dm = context.resources.displayMetrics
        val maxSide = max(dm.widthPixels, dm.heightPixels)
        val src = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
            val s = max(info.size.width, info.size.height)
            if (s > maxSide) d.setTargetSampleSize(s / maxSide)
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        store.wallpaperFile.outputStream().use { src.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        store.subjectFile.delete()
        val subject = runCatching { segment(src) }.getOrNull() ?: return@withContext false
        store.subjectFile.outputStream().use { subject.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    }

    private suspend fun segment(bmp: Bitmap): Bitmap? = suspendCancellableCoroutine { cont ->
        val seg = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
        )
        seg.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { cont.resume(it.foregroundBitmap) }
            .addOnFailureListener { cont.resume(null) }
            .addOnCompleteListener { seg.close() }
    }

    fun load(path: java.io.File): Bitmap? =
        if (path.exists()) BitmapFactory.decodeFile(path.absolutePath) else null
}
