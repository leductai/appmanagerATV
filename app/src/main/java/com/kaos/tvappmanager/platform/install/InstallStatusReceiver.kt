package com.kaos.tvappmanager.platform.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.kaos.tvappmanager.core.AppLogger
import com.kaos.tvappmanager.platform.PlatformError

data class InstallOutcome(
    val packageName: String?,
    val sessionId: Int,
    val status: Int,
    val statusMessage: String?,
    val conflictingPackage: String?,
) {
    val isSuccess: Boolean get() = status == PackageInstaller.STATUS_SUCCESS

    val error: PlatformError?
        get() = when {
            isSuccess -> null
            status == PackageInstaller.STATUS_FAILURE_ABORTED -> PlatformError.CANCELLED
            else -> PlatformError.fromInstallMessage(statusMessage)
        }
}

/**
 * Nhan ket qua commit PackageInstaller. Ket qua den DAY la khong con qua giao dien
 * (chuyen Activity) — no di vao Channel cua hang doi, va hang doi se chuyen muc tiep theo.
 */
class InstallStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_RESULT) return
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                logger(context).log(TAG, "mo hop thoai xac nhan cai dat")
                context.startActivity(confirmation)
            } else {
                logger(context).log(TAG, "PackageInstaller yeu cau xac nhan nhung khong co intent")
            }
            return
        }

        val outcome = InstallOutcome(
            packageName = intent.getStringExtra(EXTRA_PACKAGE),
            sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1),
            status = status,
            statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
            conflictingPackage = intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME),
        )
        if (!outcome.isSuccess && outcome.conflictingPackage != null) {
            logger(context).log(
                TAG,
                "cai dat bi huy do trung khoi su dung voi ${outcome.conflictingPackage}: " +
                    (outcome.statusMessage ?: "khong ro ly do")
            )
        }
        logger(context).log(
            TAG,
            "ket qua session=${outcome.sessionId} package=${outcome.packageName} " +
                "status=${outcome.status} message=${outcome.statusMessage}"
        )
        handler?.invoke(outcome)
    }

    private fun logger(context: Context): com.kaos.tvappmanager.core.AppLogger =
        (context.applicationContext as? com.kaos.tvappmanager.TvAppApplication)?.container?.logger
            ?: com.kaos.tvappmanager.core.AppLogger()

    companion object {
        const val ACTION_INSTALL_RESULT = "com.kaos.tvappmanager.action.INSTALL_RESULT"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_PACKAGE = "extra_package"

        private const val TAG = "InstallReceiver"

        @Volatile
        var handler: ((InstallOutcome) -> Unit)? = null
    }
}
