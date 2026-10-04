package com.kaos.tvappmanager.platform.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import com.kaos.tvappmanager.core.AppLogger
import com.kaos.tvappmanager.data.manifest.ManifestParser
import com.kaos.tvappmanager.platform.PlatformError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

data class DownloadResult(
    val file: File? = null,
    val error: PlatformError? = null,
) {
    val isSuccess: Boolean get() = file != null
}

/**
 * Tai APK qua HTTPS, co tien do, co the huy, kiem tru dung luong truoc.
 * Chi tra ve tien mat qua onProgress — khong luu `java.io.InputStream` noi ngoai.
 */
class ApkDownloader(
    private val context: Context,
    private val client: OkHttpClient,
    private val logger: AppLogger,
    private val wifiOnly: () -> Boolean,
) {

    suspend fun download(
        url: String,
        target: File,
        onProgress: (read: Long, total: Long) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        if (!ManifestParser.isHttps(url)) {
            return@withContext DownloadResult(error = PlatformError.NETWORK)
        }
        if (wifiOnly() && !hasNonMobileNetwork()) {
            logger.log(TAG, "tu choi tai vi dang o che do chi Wi-Fi")
            return@withContext DownloadResult(error = PlatformError.NETWORK)
        }

        var knownLength = -1L
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.log(TAG, "HTTP ${response.code} khi tai $url")
                    return@withContext DownloadResult(error = PlatformError.NETWORK)
                }
                val body = response.body ?: return@withContext DownloadResult(error = PlatformError.NETWORK)
                knownLength = body.contentLength()

                target.parentFile?.mkdirs()
                if (knownLength > 0) {
                    val needed = requiredBytes()
                    if (needed > 0 && needed < knownLength) {
                        logger.log(TAG, "khong du dung luong: can $needed, can $knownLength")
                        target.delete()
                        return@withContext DownloadResult(error = PlatformError.STORAGE)
                    }
                }

                var read = 0L
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            read += n
                            onProgress(read, knownLength)
                        }
                    }
                }

                if (knownLength > 0 && read != knownLength) {
                    logger.log(TAG, "thieu byte: $read/$knownLength")
                    target.delete()
                    return@withContext DownloadResult(error = PlatformError.NETWORK)
                }
                return@withContext DownloadResult(file = target)
            }
        } catch (e: IOException) {
            logger.log(TAG, "loi mang khi tai $url", e)
            target.delete()
            return@withContext DownloadResult(error = PlatformError.NETWORK)
        } catch (e: Exception) {
            logger.log(TAG, "loi khong mong doi khi tai $url", e)
            target.delete()
            return@withContext DownloadResult(error = PlatformError.UNKNOWN)
        }
    }

    private fun hasNonMobileNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    /** Dung luong co the dung duoc, hoac -1 neu khong do duoc. */
    private fun requiredBytes(): Long = try {
        val stat = StatFs(context.cacheDir.path)
        stat.availableBlocksLong * stat.blockSizeLong
    } catch (e: Exception) {
        -1L
    }

    private companion object {
        const val TAG = "ApkDownloader"
    }
}
