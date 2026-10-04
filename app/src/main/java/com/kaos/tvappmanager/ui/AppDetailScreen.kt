package com.kaos.tvappmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kaos.tvappmanager.R
import com.kaos.tvappmanager.data.apps.AppEntry
import com.kaos.tvappmanager.ui.components.AppIcon
import com.kaos.tvappmanager.ui.components.StatusBadge
import com.kaos.tvappmanager.ui.components.TvButton
import com.kaos.tvappmanager.ui.components.rememberAppIcon
import com.kaos.tvappmanager.di.AppContainer
import java.util.Locale

/**
 * Trang chi tiet mot app: version tren may, version trong manifest, package name,
 * dung luong APK neu biet, link tai rut gon, va cac hanh dong Cai / Cap nhat / Mo / Go.
 */
@Composable
fun AppDetailScreen(
    entry: AppEntry,
    container: AppContainer,
    canInstall: Boolean,
    onInstallOrUpdate: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    onAppInfo: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = rememberAppIcon(entry, entry.installed?.icon, container.iconCache)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 90.dp, vertical = 50.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon = icon, name = entry.name)
            Spacer(Modifier.width(24.dp))
            Column {
                Text(
                    text = entry.name,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                StatusBadge(entry.status)
            }
        }

        Spacer(Modifier.height(36.dp))

        DetailRow(stringResource(R.string.detail_installed_version), entry.installedVersion ?: "—")
        DetailRow(stringResource(R.string.detail_version_manifest), entry.manifestVersion ?: "—")
        DetailRow(stringResource(R.string.detail_package), entry.packageName)
        DetailRow(stringResource(R.string.detail_apk_url), shortenUrl(entry.apkUrl))
        if (entry.manifestApp?.sha256 != null) {
            DetailRow("SHA-256", entry.manifestApp.sha256.take(24) + "…")
        }
        if (entry.installed != null && entry.installed.versionCode > 0) {
            DetailRow("versionCode", entry.installed.versionCode.toString())
        }
        if (entry.installed?.isSystem == true) {
            DetailRow(stringResource(R.string.detail_not_installed), "")
        }

        Spacer(Modifier.height(42.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            val needsInstall = !entry.isInstalled || entry.status == AppEntry.Status.HAS_UPDATE
            if (entry.apkUrl.isNotBlank()) {
                TvButton(
                    label = stringResource(
                        if (needsInstall) R.string.btn_install else R.string.btn_update
                    ),
                    onClick = onInstallOrUpdate,
                    enabled = canInstall,
                    requestFocusOnCompose = true,
                )
                Spacer(Modifier.width(16.dp))
            }
            if (entry.isInstalled) {
                TvButton(label = stringResource(R.string.btn_open), onClick = onOpen)
                Spacer(Modifier.width(16.dp))
                TvButton(label = stringResource(R.string.btn_app_info), onClick = onAppInfo)
                Spacer(Modifier.width(16.dp))
                if (entry.packageName != "com.kaos.tvappmanager") {
                    TvButton(label = stringResource(R.string.btn_uninstall), onClick = onUninstall)
                }
                Spacer(Modifier.width(16.dp))
            }
            TvButton(label = stringResource(R.string.btn_back), onClick = onBack)
        }

        if (!canInstall && entry.apkUrl.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.permission_missing_title),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isEmpty()) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = label,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(300.dp),
        )
        Text(
            text = value,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** Rut gon link dai de khop mot dong tren TV. */
private fun shortenUrl(url: String): String {
    if (url.length <= 58) return url
    return url.take(34) + "…" + url.takeLast(20)
}
