package com.kaos.tvappmanager.platform.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Quyen cai ung dung tu nguon khong xac duoc (cau hinh cho viec cai/buy khi khong co root).
 * Ung dung van mo duoc khi thieu quyen — chi se vo hieu hoa cac nut cai.
 */
class InstallPermission(private val context: Context) {

    fun has(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS, 0) == 1
        }

    fun openSystemSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { activity.startActivity(intent) }
                .getOrElse { activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
        } else {
            @Suppress("DEPRECATION")
            activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        }
    }

    /** Quyen cai dat dung cho bo nho he thong (khong dung cho bo nho USB/odc). */
    fun canInstallFromPackageManager(): Boolean =
        context.packageManager.canRequestPackageInstalls()
}

/** Quyen doc quyen cua mot package cu the de hien thi hop thoai cua he thong. */
fun Context.hasPackageInfoPermission(packageName: String): Boolean =
    try {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
