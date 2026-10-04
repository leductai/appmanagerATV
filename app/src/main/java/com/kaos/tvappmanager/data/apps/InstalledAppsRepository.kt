package com.kaos.tvappmanager.data.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val icon: Drawable?,
    val apkSizeBytes: Long,
) {
    /** Lay manifest version, dung khi versionMode = raw. */
    fun manifestVersionName(): String = versionName
}

class InstalledAppsRepository(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    suspend fun listAll(includeSystem: Boolean = true): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val flags = PackageManager.GET_META_DATA
            val packages = try {
                if (includeSystem) pm.getInstalledPackages(flags)
                else pm.getInstalledPackages(flags).filter { info ->
                    (info.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM == 0
                }
            } catch (e: Exception) {
                emptyList()
            }
            packages.mapNotNull { info ->
                runCatching { toInstalledApp(info) }.getOrNull()
            }.sortedWith(compareBy({ !it.isSystem }, { it.label.lowercase() }))
        }

    suspend fun find(packageName: String): InstalledApp? = withContext(Dispatchers.IO) {
        val info = try {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } ?: return@withContext null
        runCatching { toInstalledApp(info) }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun toInstalledApp(info: PackageInfo): InstalledApp {
        val ai = info.applicationInfo
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        return InstalledApp(
            packageName = info.packageName,
            label = ai?.let { pm.getApplicationLabel(it).toString() } ?: info.packageName,
            versionName = info.versionName ?: "",
            versionCode = versionCode,
            isSystem = (ai?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0,
            icon = runCatching { ai?.loadIcon(pm) }.getOrNull(),
            apkSizeBytes = runCatching { (ai?.sourceDir?.length ?: 0).toLong() }.getOrDefault(0L),
        )
    }

    companion object {
        /** Bitmap hoa Drawable, dung cho hien thi trong Compose. */
        fun toBitmap(drawable: Drawable, sizePx: Int): Bitmap? {
            if (drawable is BitmapDrawable) {
                val existing = drawable.bitmap ?: return null
                return Bitmap.createScaledBitmap(existing, sizePx, sizePx, true)
            }
            return try {
                val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            } catch (e: Exception) {
                null
            }
        }
    }
}
