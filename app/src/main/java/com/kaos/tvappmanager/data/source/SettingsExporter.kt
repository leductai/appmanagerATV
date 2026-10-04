package com.kaos.tvappmanager.data.source

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.kaos.tvappmanager.core.AppLogger
import java.io.File

/**
 * Xuat cau hinh va log ra thu muc Downloads cua TV.
 *
 * KHONG bao gio xuat file APK da tai — chi xuat cac nguon va tuy chon.
 * Log noi bo chi duoc ghi ra khi nguoi dung chon trong menu cai dat an.
 */
class SettingsExporter(
    private val context: Context,
    private val sourceStore: SourceStore,
    private val logger: AppLogger,
) {

    suspend fun exportConfig(): Uri? {
        val snapshot = sourceStore.snapshot()
        val body = buildString {
            appendLine("{")
            appendLine("  \"exportedAt\": \"${java.time.Instant.now()}\",")
            appendLine("  \"activeSource\": ${snapshot.activeSource?.id?.let { "\"$it\"" } ?: "null"},")
            appendLine("  \"wifiOnly\": ${snapshot.wifiOnly},")
            appendLine("  \"timeoutSeconds\": ${snapshot.timeoutSeconds},")
            appendLine("  \"autoDeleteApk\": ${snapshot.autoDeleteApk},")
            appendLine("  \"language\": \"${snapshot.language.code}\",")
            appendLine("  \"sources\": [")
            snapshot.sources.forEachIndexed { index, source ->
                appendLine(
                    """    {"name": "${source.name.escape()}", "location": "${source.location.escape()}"}""" +
                        if (index == snapshot.sources.lastIndex) "" else ","
                )
            }
            appendLine("  ]")
            append("}")
        }
        return writeToDownloads("tvappmanager-config.json", body)
    }

    fun exportLog(): Uri? = writeToDownloads("tvappmanager-log.txt", logger.asText())

    private fun writeToDownloads(fileName: String, body: String): Uri? = runCatching {
        // Android 10+ khong con cho ghi truc tiep vao /sdcard/Download.
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                .also { it.writeText(body) }
                .toString()
                .let(Uri::parse)

        if (uri.scheme == "content") {
            resolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        }
        logger.log(TAG, "da xuat $fileName")
        uri
    }.getOrElse {
        logger.log(TAG, "khong xuat duoc $fileName", it)
        null
    }

    fun tempApkStats(): Pair<Int, Long> {
        val dir = File(context.cacheDir, "apk")
        val files = dir.listFiles()?.filter { it.isFile } ?: return 0 to 0L
        return files.size to files.sumOf { it.length() }
    }

    fun cleanTemp(): Pair<Int, Long> {
        val (count, bytes) = tempApkStats()
        File(context.cacheDir, "apk").listFiles()?.forEach { runCatching { it.delete() } }
        return count to bytes
    }

    private fun String.escape(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

    private companion object {
        const val TAG = "SettingsExport"
    }
}
