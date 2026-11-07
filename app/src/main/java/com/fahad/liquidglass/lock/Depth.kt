package com.fahad.liquidglass.lock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.app.WallpaperManager
import android.net.Uri
import android.os.Environment
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import androidx.core.graphics.drawable.toBitmap
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

    /** Can we read the phone's real wallpaper? Android 13+ needs "all files access" for that. */
    fun canReadWallpaper(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager() ||
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun currentWallpaperId(context: Context): Int = runCatching {
        val wm = WallpaperManager.getInstance(context)
        val lock = wm.getWallpaperId(WallpaperManager.FLAG_LOCK)
        if (lock > 0) lock else wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
    }.getOrDefault(-1)

    /** Reads the lock-screen wallpaper (or the home one if there is none) and cuts out the subject. */
    suspend fun importSystemWallpaper(context: Context, store: LockStore): Result = withContext(Dispatchers.IO) {
        if (!canReadWallpaper(context)) return@withContext Result.NO_PERMISSION
        val wm = WallpaperManager.getInstance(context)
        val bmp = listOf(WallpaperManager.FLAG_LOCK, WallpaperManager.FLAG_SYSTEM).firstNotNullOfOrNull { flag ->
            runCatching {
                wm.getWallpaperFile(flag)?.use { BitmapFactory.decodeFileDescriptor(it.fileDescriptor) }
            }.getOrNull()
        } ?: runCatching { wm.drawable?.toBitmap() }.getOrNull()
            ?: return@withContext Result.UNREADABLE
        val maxSide = max(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)
        val scaled = if (max(bmp.width, bmp.height) > maxSide) {
            val k = maxSide.toFloat() / max(bmp.width, bmp.height)
            Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true)
        } else bmp
        store.wallpaperFile.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        store.subjectFile.delete()
        val subject = runCatching { segment(scaled) }.getOrNull()
        if (subject != null) store.subjectFile.outputStream().use { subject.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (subject != null) Result.OK_WITH_SUBJECT else Result.OK
    }

    enum class Result { OK, OK_WITH_SUBJECT, NO_PERMISSION, UNREADABLE }

    /** Re-imports when the user changed their wallpaper since last time. Returns the updated layout. */
    suspend fun syncWithSystem(context: Context, store: LockStore): LockLayout {
        val l = store.load()
        if (!l.followSystem) return l
        val id = currentWallpaperId(context)
        if (l.hasWallpaper && id == l.systemWallId && id > 0) return l
        val r = importSystemWallpaper(context, store)
        if (r == Result.NO_PERMISSION || r == Result.UNREADABLE) return l
        val n = l.copy(hasWallpaper = true, hasSubject = r == Result.OK_WITH_SUBJECT, systemWallId = id, version = System.currentTimeMillis())
        store.save(n)
        return n
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
