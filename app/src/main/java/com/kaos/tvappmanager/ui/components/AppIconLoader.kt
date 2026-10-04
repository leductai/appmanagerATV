package com.kaos.tvappmanager.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.kaos.tvappmanager.data.apps.AppEntry
import com.kaos.tvappmanager.data.apps.InstalledAppsRepository
import com.kaos.tvappmanager.data.icon.IconCache

private const val ICON_PX = 128

/**
 * Lay icon hien thi cho mot app. Man hinh KHONG cho dung icon moi: phan thien
 * xien hien ngay (icon da cai tren may) va nhung icon dang tai se duoc hien thi
 * sau khi xong — khong giam kich thuoc, khong lam ban do nham khi chua tai xong.
 */
@Composable
fun rememberAppIcon(
    entry: AppEntry?,
    installedDrawable: android.graphics.drawable.Drawable?,
    iconCache: IconCache,
): ImageBitmap? {
    val key = entry?.packageName.orEmpty()
    var remote by remember(key) { mutableStateOf<Bitmap?>(null) }

    val installedBitmap = remember(installedDrawable, key) {
        installedDrawable?.let { InstalledAppsRepository.toBitmap(it, ICON_PX) }
    }

    LaunchedEffect(key, entry?.iconUrl) {
        if (installedBitmap != null) return@LaunchedEffect
        if (entry == null) return@LaunchedEffect
        remote = iconCache.load(entry.iconUrl)
    }

    val resolved = installedBitmap ?: remote
    return resolved?.asImageBitmap()
}
