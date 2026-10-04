package com.kaos.tvappmanager.data.icon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.kaos.tvappmanager.core.AppLogger
import com.kaos.tvappmanager.data.manifest.ManifestParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * Tai icon tu `iconUrl` va cache theo URL. Bat ky loi nao (URL khong hop le,
 * HTTP loi, anh hong dung) deu tra null de UI dung icon mac dinh cua TV App Manager.
 * Man hinh khong bao gio cho dung icon moi truocc khi hien thi du lieu app.
 */
class IconCache(
    private val context: Context,
    private val client: OkHttpClient,
    private val logger: AppLogger,
) {

    private val memory = object : LruCache<String, Bitmap>(MEMORY_ENTRIES) {
        override fun sizeOf(key: String, value: Bitmap) = 1
    }

    private val dir: File by lazy {
        File(context.cacheDir, "icons").apply { if (!exists()) mkdirs() }
    }

    fun cached(url: String): Bitmap? = memory.get(key(url)) ?: decodeFromDisk(url)

    suspend fun load(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        if (!ManifestParser.isHttps(url)) return null
        cached(url)?.let { return it }

        val remote = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    response.body?.bytes() ?: error("Response rong.")
                }
            }
        }.getOrElse { error ->
            logger.log(TAG, "tai icon that bai $url: ${error.message}")
            return null
        }

        val bitmap = withContext(Dispatchers.IO) {
            decodeBytes(remote)
        }
        if (bitmap == null) {
            logger.log(TAG, "icon khong phai anh hop le: $url")
            return null
        }
        memory.put(key(url), bitmap)
        withContext(Dispatchers.IO) { runCatching { File(dir, key(url)).writeBytes(remote) } }
        return bitmap
    }

    fun cacheSizeBytes(): Long =
        dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clear() {
        memory.evictAll()
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    private fun decodeFromDisk(url: String): Bitmap? = runCatching {
        File(dir, key(url)).takeIf { it.exists() }?.let { decodeBytes(it.readBytes()) }
    }.getOrNull()

    /** Bitmap giam kich thuoc san, tranh giu nhieu bitmap la trong bo dem. */
    private fun decodeBytes(bytes: ByteArray): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_PX && bounds.outHeight / (sample * 2) >= TARGET_PX) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }.getOrNull()

    private fun key(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "IconCache"
        const val MEMORY_ENTRIES = 64
        const val TARGET_PX = 160
    }
}
