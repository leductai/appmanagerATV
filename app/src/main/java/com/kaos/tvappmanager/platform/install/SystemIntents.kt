package com.kaos.tvappmanager.platform.install

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Goi giao dien xac nhan goc cua he thong cho cac hanh dong cai dat va go cai dat.
 * Khong dong gop — thong dieo "khong ho tro cai im lang" la mot rang buoc he thong.
 */
object SystemIntents {

    const val REQUEST_CODE_UNINSTALL = 4401

    /** Yeu cau goi hop thoai go cai dat cua he thong. Luon co xac nhan truoc. */
    fun uninstall(activity: Activity, packageName: String) {
        if (packageName == activity.packageName) return
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivityForResult(intent, REQUEST_CODE_UNINSTALL)
    }

    /** Mo man hinh thong tin he thong cho mot app. */
    fun appInfo(activity: Activity, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { activity.startActivity(intent) }
    }

    /** Mo ung dung da cai (yem cau cai 2 phia kieu ACTION_MAIN). */
    fun openApp(activity: Activity, packageName: String) {
        val launch = activity.packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: activity.packageManager.getLaunchIntentForPackage(packageName)
        if (launch == null) return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        activity.startActivity(launch)
    }
}
