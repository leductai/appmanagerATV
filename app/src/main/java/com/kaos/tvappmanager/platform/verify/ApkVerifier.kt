package com.kaos.tvappmanager.platform.verify

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.kaos.tvappmanager.core.AppLogger
import com.kaos.tvappmanager.data.manifest.ManifestApp
import com.kaos.tvappmanager.data.manifest.VersionComparator
import com.kaos.tvappmanager.platform.PlatformError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

sealed interface VerifyResult {
    data class Ok(val archive: PackageInfo) : VerifyResult
    data class Fail(val error: PlatformError) : VerifyResult
}

/**
 * Kiem tra tinh toan ven APK TRUOC khi goi PackageInstaller, theo spec section 12:
 * SHA-256 (neu manifest cung cap), package phai trung, va version khong duoc nho
 * hon version khai bao.
 */
class ApkVerifier(
    private val context: Context,
    private val logger: AppLogger,
) {

    suspend fun verify(file: File, expected: ManifestApp): VerifyResult =
        withContext(Dispatchers.IO) {
            if (!file.exists() || file.length() == 0L) {
                return@withContext VerifyResult.Fail(PlatformError.STORAGE)
            }

            val digest = sha256Of(file)
            val expectedHash = expected.sha256?.trim()?.lowercase()
            if (!expectedHash.isNullOrEmpty() && digest != expectedHash) {
                logger.log(TAG, "sha256 khong khop cho ${expected.packageName}: $digest != $expectedHash")
                return@withContext VerifyResult.Fail(PlatformError.CHECKSUM)
            }

            val archive = try {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            } catch (e: Exception) {
                null
            } ?: run {
                logger.log(TAG, "khong doc duoc package info cua ${file.name}")
                return@withContext VerifyResult.Fail(PlatformError.VERIFY)
            }

            if (archive.packageName != expected.packageName) {
                logger.log(TAG, "package khac: ${archive.packageName} vs ${expected.packageName}")
                return@withContext VerifyResult.Fail(PlatformError.PACKAGE_MISMATCH)
            }

            val archiveVersion = archive.versionName ?: ""
            // Chinh sach: version APK khong duoc nho hon version khai bao.
            // Neu khong so sanh duoc (UNKNOWN) thi cho phep — chinh `versionMode` cua manifest
            // la no can kich hoat de yeu cau nay co nghia.
            val order = VersionComparator.compare(
                archiveVersion,
                expected.version,
                expected.versionMode,
            )
            if (order == VersionComparator.Order.OLDER) {
                logger.log(TAG, "version APK nho hon khai bao: $archiveVersion < ${expected.version}")
                return@withContext VerifyResult.Fail(PlatformError.VERSION_MISMATCH)
            }

            logger.log(TAG, "kiem tra qua: ${expected.packageName} $archiveVersion")
            VerifyResult.Ok(archive)
        }

    private fun sha256Of(file: File): String = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrElse { "" }

    private companion object {
        const val TAG = "ApkVerifier"
    }
}
