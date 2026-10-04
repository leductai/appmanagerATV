package com.kaos.tvappmanager.platform.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.kaos.tvappmanager.core.AppLogger
import com.kaos.tvappmanager.data.manifest.ManifestApp
import com.kaos.tvappmanager.platform.PlatformError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface CommitResult {
    data object Submitted : CommitResult
    data class Failed(val error: PlatformError) : CommitResult
}

/**
 * Tao va commit PackageInstaller session de Android hien hop thoai xac nhan cai dat.
 *
 * Android chi cho phep MOT hop thoai xac nhan cai dat he thong tai mot thoi diem,
 * nen hang doi phai commit tung session va cho nhan ket qua truoc khi sang session sau.
 */
class PackageInstallerRunner(
    private val context: Context,
    private val logger: AppLogger,
) {

    private val installer: PackageInstaller get() = context.packageManager.packageInstaller

    fun tempApkDir(): File = File(context.cacheDir, "apk").apply { if (!exists()) mkdirs() }

    suspend fun commit(apk: File, app: ManifestApp): CommitResult = withContext(Dispatchers.IO) {
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setAppPackageName(app.packageName)
            setSize(apk.length())
        }

        var sessionId: Int = -1
        try {
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                val output = session.openWrite("base.apk", 0, apk.length())
                output.use { stream ->
                    apk.inputStream().use { input -> input.copyTo(stream) }
                    session.fsync(stream)
                }
                session.commit(intentSender(sessionId, app.packageName).intentSender)
            }
            logger.log(TAG, "da commit session $sessionId cho ${app.packageName}")
            CommitResult.Submitted
        } catch (e: SecurityException) {
            logger.log(TAG, "khong co quyen commit cho ${app.packageName}", e)
            abandon(sessionId)
            CommitResult.Failed(PlatformError.PERMISSION)
        } catch (e: Exception) {
            logger.log(TAG, "commit that bai cho ${app.packageName}", e)
            abandon(sessionId)
            CommitResult.Failed(PlatformError.fromInstallMessage(e.message))
        }
    }

    private fun abandon(sessionId: Int) {
        if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
    }

    /**
     * FLAG_MUTABLE bat buoc: he thong them `EXTRA_STATUS` vao Intent nay sau khi
     * nguoi dung xac nhan hay tu choi trong hop thoai he thong.
     */
    private fun intentSender(sessionId: Int, packageName: String): PendingIntent {
        val intent = Intent(context, InstallStatusReceiver::class.java).apply {
            action = InstallStatusReceiver.ACTION_INSTALL_RESULT
            putExtra(InstallStatusReceiver.EXTRA_SESSION_ID, sessionId)
            putExtra(InstallStatusReceiver.EXTRA_PACKAGE, packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            sessionId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    private companion object {
        const val TAG = "Installer"
    }
}
