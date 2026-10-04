package com.kaos.tvappmanager.platform.install

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.kaos.tvappmanager.core.AppLogger

/**
 * Goi giao dien xac nhan goc cua he thong cho cac hanh dong cai dat va go cai dat.
 * Khong co duong cai im lang qua lop nay — do la rang buoc cua he thong.
 */
object SystemIntents {

    const val REQUEST_CODE_UNINSTALL = 4401

    /**
     * Mo hop thoai go cai dat cua he thong.
     *
     * Dung `Uri.fromParts("package", ...)` thay vi ghep chuoi de URI luon dung chuan;
     * kiem tra `resolveActivity` truoc khi start de ROM thieu UninstallerActivity
     * khong lam app crash.
     */
    fun uninstall(activity: Activity, packageName: String, logger: AppLogger? = null) {
        if (packageName == activity.packageName) return
        if (packageName.isBlank()) return

        val uri = Uri.fromParts("package", packageName, null)
        val intent = Intent(Intent.ACTION_DELETE).setData(uri)

        if (intent.resolveActivity(activity.packageManager) == null) {
            logger?.log(TAG, "khong co activity xu ly ACTION_DELETE cho $packageName")
            return
        }
        runCatching {
            activity.startActivityForResult(intent, REQUEST_CODE_UNINSTALL)
        }.onFailure { error ->
            logger?.log(TAG, "khong mo duoc man hinh go cho $packageName", error)
        }
    }

    /** Mo man hinh thong tin he thong cho mot app. */
    fun appInfo(activity: Activity, packageName: String, logger: AppLogger? = null) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { activity.startActivity(intent) }
            .onFailure { logger?.log(TAG, "khong mo duoc thong tin app $packageName", it) }
    }

    /** Mo ung dung da cai. */
    fun openApp(activity: Activity, packageName: String) {
        val launch = activity.packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: activity.packageManager.getLaunchIntentForPackage(packageName)
        if (launch == null) return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { activity.startActivity(launch) }
    }

    private const val TAG = "SystemIntents"
}
