package com.kaos.tvappmanager.data.manifest

import android.content.Context
import android.net.Uri
import com.kaos.tvappmanager.core.AppLogger
import com.kaos.tvappmanager.data.source.ManifestSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

sealed interface ManifestLoad {
    data class Success(val manifest: Manifest, val fromCache: Boolean) : ManifestLoad
    data class Failure(val message: String) : ManifestLoad
}

/**
 * Nap manifest tu HTTPS hoac file local, giu ban cache lai thanh cong gan nhat
 * de van hien thi duoc danh sach khi mat mang (yeu cau spec section 11).
 */
class ManifestRepository(
    private val context: Context,
    private val client: OkHttpClient,
    private val logger: AppLogger,
) {

    private val cacheFile: File get() = File(context.filesDir, CACHE_FILE_NAME)

    suspend fun load(source: ManifestSource): ManifestLoad = withContext(Dispatchers.IO) {
        val raw = when {
            source.location.startsWith("https://") -> fetchHttps(source.location)
            source.location.startsWith("file://") ->
                readFile(Uri.parse(source.location).path ?: source.location.removePrefix("file://"))
            source.location.startsWith("content://") -> readContent(Uri.parse(source.location))
            else -> Result.failure(IllegalArgumentException("Duong dan khong ho tro: ${source.location}"))
        }

        val json = raw.getOrNull()
        if (json != null) {
            parseAndCache(json, source)
        } else {
            readCacheOrFail(raw.exceptionOrNull() ?: IllegalStateException("Khong doc duoc manifest."))
        }
    }

    private fun parseAndCache(json: String, source: ManifestSource): ManifestLoad {
        val parsed = ManifestParser.parse(json)
        if (parsed.manifest == null) {
            val message = parsed.error ?: "Manifest khong doc duoc."
            logger.log(TAG, "parse that bai cho ${source.name}: $message")
            return readCacheOrFail(IllegalStateException(message))
        }
        runCatching { cacheFile.writeText(json) }
            .onFailure { logger.log(TAG, "khong ghi duoc manifest cache", it) }
        if (parsed.warnings.isNotEmpty()) {
            logger.log(TAG, "bo qua ${parsed.warnings.size} muc: ${parsed.warnings.joinToString("; ")}")
        }
        return ManifestLoad.Success(parsed.manifest, fromCache = false)
    }

    private fun readCacheOrFail(cause: Throwable): ManifestLoad {
        val cached = runCatching { cacheFile.readText() }.getOrNull()
        if (cached == null) {
            logger.log(TAG, "khong co manifest cache de fallback", cause)
            return ManifestLoad.Failure(cause.message ?: cause.javaClass.simpleName)
        }
        logger.log(TAG, "dung manifest cache: ${cause.message}")
        val parsed = ManifestParser.parse(cached)
        val manifest = parsed.manifest ?: return ManifestLoad.Failure("Manifest cache khong hieu.")
        return ManifestLoad.Success(manifest, fromCache = true)
    }

    private fun fetchHttps(url: String): Result<String> = runCatching {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string() ?: error("Response rong.")
        }
    }

    private fun readFile(path: String): Result<String> = runCatching {
        val file = File(path)
        if (!file.exists() || !file.canRead()) error("Khong doc duoc file: $path")
        file.readText()
    }

    private fun readContent(uri: Uri): Result<String> = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Khong mo duoc noi dung: $uri")
    }

    fun cacheSizeBytes(): Long = cacheFile.length()

    fun clearCache() {
        runCatching { cacheFile.delete() }
    }

    private companion object {
        const val TAG = "ManifestRepo"
        const val CACHE_FILE_NAME = "manifest-cache.json"
    }
}
