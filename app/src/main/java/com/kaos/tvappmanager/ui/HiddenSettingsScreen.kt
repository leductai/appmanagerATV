package com.kaos.tvappmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import com.kaos.tvappmanager.BuildConfig
import com.kaos.tvappmanager.R
import com.kaos.tvappmanager.data.source.LanguagePreference
import com.kaos.tvappmanager.ui.components.TvButton

/**
 * Menu cai dat AN. Khong xuat hien tren tab hay man hinh chinh — chi mo bang chuoi
 * phim so `8-8-0-0` trong 4 giay. Danh cho nguoi quan tri (spec section 9).
 *
 * Khi dong menu, app tro ve man hinh truoc va khoi phuc focus.
 */
@Composable
fun HiddenSettingsScreen(
    state: MainUiState,
    permissionGranted: Boolean,
    wifiOnly: Boolean,
    timeoutSeconds: Int,
    autoDeleteApk: Boolean,
    language: LanguagePreference,
    logLines: List<String>,
    cacheBytes: Long,
    tempFileCount: Int,
    tempFileBytes: Long,
    onWifiOnlyChange: (Boolean) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onAutoDeleteChange: (Boolean) -> Unit,
    onLanguageChange: (LanguagePreference) -> Unit,
    onOpenSources: () -> Unit,
    onImportManifest: () -> Unit,
    onExportConfig: () -> Unit,
    onOpenPermission: () -> Unit,
    onClearCache: () -> Unit,
    onCleanTemp: () -> Unit,
    onClearLog: () -> Unit,
    onExportLog: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var languageDialog by remember { mutableStateOf(false) }
    var timeoutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 80.dp, vertical = 40.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(18.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            item {
                SettingsItem(
                    title = stringResource(R.string.settings_sources),
                    subtitle = state.settings?.activeSource?.name
                        ?: stringResource(R.string.sources_empty),
                    value = "",
                    onClick = onOpenSources,
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.settings_import),
                    subtitle = "JSON",
                    value = "",
                    onClick = onImportManifest,
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.settings_export),
                    subtitle = "",
                    value = "",
                    onClick = onExportConfig,
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.settings_permission),
                    subtitle = if (permissionGranted) stringResource(R.string.permission_already_granted)
                    else stringResource(R.string.permission_missing_title),
                    value = "",
                    onClick = onOpenPermission,
                )
            }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.network_wifi_only),
                    subtitle = "",
                    checked = wifiOnly,
                    onChange = onWifiOnlyChange,
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.network_timeout),
                    subtitle = "",
                    value = "$timeoutSeconds s",
                    onClick = { timeoutDialog = true },
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.network_clear_cache),
                    subtitle = "",
                    value = formatBytes(cacheBytes),
                    onClick = onClearCache,
                )
            }
            item {
                SettingsSwitch(
                    title = stringResource(R.string.storage_auto_delete),
                    subtitle = "",
                    checked = autoDeleteApk,
                    onChange = onAutoDeleteChange,
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.storage_clean_tmp),
                    subtitle = "",
                    value = "$tempFileCount · ${formatBytes(tempFileBytes)}",
                    onClick = onCleanTemp,
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.language_label),
                    subtitle = "",
                    value = languageLabel(language),
                    onClick = { languageDialog = true },
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.settings_diagnostics),
                    subtitle = "",
                    value = stringResource(R.string.diagnostics_clear),
                    onClick = onClearLog,
                )
            }
            item {
                SettingsItem(
                    title = stringResource(R.string.settings_about),
                    subtitle = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    value = stringResource(R.string.diagnostics_export),
                    onClick = onExportLog,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        TvButton(label = stringResource(R.string.btn_back), onClick = onBack, requestFocusOnCompose = true)
    }

    if (timeoutDialog) {
        OptionDialog(
            title = stringResource(R.string.network_timeout),
            options = TIMEOUT_PRESETS.map { "$it s" },
            selectedIndex = TIMEOUT_PRESETS.indexOf(timeoutSeconds).takeIf { it >= 0 } ?: 1,
            onSelect = { onTimeoutChange(TIMEOUT_PRESETS[it]) },
            onDismiss = { timeoutDialog = false },
        )
    }

    if (languageDialog) {
        val options = listOf(
            stringResource(R.string.language_system),
            stringResource(R.string.language_vi),
            stringResource(R.string.language_en),
        )
        val values = listOf(
            LanguagePreference.SYSTEM,
            LanguagePreference.VI,
            LanguagePreference.EN,
        )
        OptionDialog(
            title = stringResource(R.string.language_label),
            options = options,
            selectedIndex = values.indexOf(language).takeIf { it >= 0 } ?: 0,
            onSelect = { onLanguageChange(values[it]) },
            onDismiss = { languageDialog = false },
        )
    }
}

private val TIMEOUT_PRESETS = listOf(15, 30, 60, 120, 300)

@Composable
private fun OptionDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .width(720.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(36.dp),
        ) {
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(18.dp))
            options.forEachIndexed { index, option ->
                TvButton(
                    label = option,
                    onClick = { onSelect(index) },
                    requestFocusOnCompose = index == selectedIndex,
                    emphasize = index == selectedIndex,
                )
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(8.dp))
            TvButton(label = stringResource(R.string.queue_cancel), onClick = onDismiss)
        }
    }
}

/**
 * Mot dong = MOT muc focus. Tach nhan va nut thanh hai muc se buoc nguoi dung
 * bam 2 lan cho mot hanh dong, va rat de lam day phan ben phai man hinh TV.
 */
@Composable
private fun SettingsItem(title: String, subtitle: String, value: String, onClick: () -> Unit) {
    androidx.tv.material3.Button(
        onClick = onClick,
        colors = androidx.tv.material3.ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                }
            }
            if (value.isNotBlank()) {
                Spacer(Modifier.width(16.dp))
                Text(
                    text = value,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.tv.material3.Button(
        onClick = { onChange(!checked) },
        colors = androidx.tv.material3.ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            // Trang thai luon co chu ben canh, khong chi dua vao mau (spec section 5).
            Text(
                text = if (checked) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(14.dp))
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

private fun languageLabel(preference: LanguagePreference): String = when (preference) {
    LanguagePreference.SYSTEM -> "System"
    LanguagePreference.VI -> "Tiếng Việt"
    LanguagePreference.EN -> "English"
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
