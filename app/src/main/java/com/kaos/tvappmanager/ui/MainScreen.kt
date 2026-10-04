package com.kaos.tvappmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import com.kaos.tvappmanager.R
import com.kaos.tvappmanager.data.apps.AppCatalog
import com.kaos.tvappmanager.data.apps.AppEntry
import com.kaos.tvappmanager.di.AppContainer
import com.kaos.tvappmanager.ui.components.ActionButtonsRow
import com.kaos.tvappmanager.ui.components.AppCard
import com.kaos.tvappmanager.ui.components.AppCardSlot
import com.kaos.tvappmanager.ui.components.MainFocusState
import com.kaos.tvappmanager.ui.components.SectionHeader
import com.kaos.tvappmanager.ui.components.rememberAppIcon
import com.kaos.tvappmanager.platform.install.QueueState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Mot hang trong man hinh chinh: hoac la tieu de nhom, hoac la 1-2 the. */
private data class ContentRow(
    @androidx.annotation.StringRes val header: Int,
    val cards: List<AppEntry>,
    val rowIndex: Int,
) {
    val isHeader: Boolean get() = header != 0
}

/**
 * Man hinh chinh.
 *
 * Luat focus (spec section 5):
 * - `Up` tu the hang dau tien -> vung hanh dong, dung nhat o "Cập nhật tất cả".
 * - `Up` tu vung hanh dong -> dung vi tri, khong bo loi.
 * - `Down` tu "Cập nhật tất ca" -> the dau tien trong nhom "Co ban cap nhat".
 * - `Down` tu "Cài tất ca" -> the dau tien trong nhom "Chua cai dat" (grid tu cuon toi).
 * - `Left` / `Right` giua hai nut hanh dong -> tu nhien di chuyen, khong can can thiep.
 */
@Composable
fun MainScreen(
    state: MainUiState,
    queue: QueueState,
    container: AppContainer,
    focusState: MainFocusState,
    onOpenDetail: (AppEntry) -> Unit,
    onQueueStateClick: () -> Unit,
    onUpdateAll: () -> Unit,
    onInstallAll: () -> Unit,
    onOpenHiddenSettings: () -> Unit,
    onRetryManifest: () -> Unit,
    onRequestExit: () -> Unit,
    onTabChange: (MainTab) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val rows = remember(state.groups) { buildRows(state.groups) }
    val firstCardRowIndex = remember(rows) {
        rows.firstOrNull { it.cards.isNotEmpty() }?.rowIndex ?: -1
    }
    val firstUpdateRequester = remember { FocusRequester() }
    val firstNotInstalledRequester = remember { FocusRequester() }

    BackHandler { onRequestExit() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Bang luat focus nay chay TRUOC moi element con, va chi chay o man hinh chinh.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> when {
                        focusState.focusedRowIndex == firstCardRowIndex -> {
                            focusState.updateAllRequester.requestFocus()
                            true
                        }

                        focusState.focusedRowIndex > 0 -> false
                        else -> true
                    }

                    Key.DirectionDown -> when (focusState.focusedAction) {
                        MainFocusState.ACTION_UPDATE_ALL -> {
                            val target = rows.firstOrNull { it.rowIndex == firstCardRowIndex }?.cards?.firstOrNull()
                            if (target != null) {
                                focusState.requesterFor(target.packageName)?.requestFocus()
                            } else {
                                focusState.updateAllRequester.requestFocus()
                            }
                            true
                        }

                        MainFocusState.ACTION_INSTALL_ALL -> {
                            val notInstalled = state.groups.notInstalled.firstOrNull()
                            if (notInstalled != null) {
                                focusState.requesterFor(notInstalled.packageName)?.requestFocus()
                                    ?: firstNotInstalledRequester.requestFocus()
                            } else {
                                focusState.installAllRequester.requestFocus()
                            }
                            true
                        }

                        else -> false
                    }

                    else -> false
                }
            }
            .padding(contentPadding),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 28.dp)) {
            Header(onRetryManifest = onRetryManifest, usingCached = state.usingCachedManifest)

            ActionButtonsRow(
                updateCount = state.updateCount,
                installCount = state.installCount,
                queueRunning = queue.running,
                queueDone = queue.finishedCount,
                queueTotal = queue.total,
                hasActiveSource = state.hasActiveSource,
                canInstall = state.canInstall,
                focusState = focusState,
                onQueueStateClick = onQueueStateClick,
                onUpdateAll = onUpdateAll,
                onInstallAll = onInstallAll,
            )

            Tabs(selected = state.tab, onSelect = onTabChange)

            // Thieu quyen cai: app VAN dung duoc, chi cac nut cai bi vo hieu hoa
            // va hien huong dan ngan (spec section 3).
            if (!state.canInstall) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.permission_missing_title) + " — " +
                        stringResource(R.string.permission_missing_body),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }

            Spacer(Modifier.height(6.dp))

            if (state.tab == MainTab.INSTALLED) {
                InstalledList(
                    state = state,
                    container = container,
                    focusState = focusState,
                    onOpenDetail = onOpenDetail,
                    modifier = Modifier.weight(1f),
                )
            } else {
                UpdatesList(
                    state = state,
                    queue = queue,
                    container = container,
                    focusState = focusState,
                    onOpenDetail = onOpenDetail,
                    onRetryManifest = onRetryManifest,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun UpdatesList(
    state: MainUiState,
    queue: QueueState,
    container: AppContainer,
    focusState: MainFocusState,
    onOpenDetail: (AppEntry) -> Unit,
    onRetryManifest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(state.groups) { buildRows(state.groups) }

    when {
        state.loading -> LoadingText()
        state.manifestError != null -> ManifestError(text = state.manifestError)
        !state.hasActiveSource -> NoSourceHint()
        rows.isEmpty() -> EmptyText(stringResource(R.string.state_updates_empty))
        else -> LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(rows, key = { it.rowIndex }) { row ->
                if (row.isHeader) {
                    SectionHeader(stringResource(row.header))
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        row.cards.forEach { entry ->
                            AppCardSlot {
                                AppCardRow(
                                    entry = entry,
                                    container = container,
                                    focusState = focusState,
                                    rowIndex = row.rowIndex,
                                    onClick = { onOpenDetail(entry) },
                                )
                            }
                        }
                        if (row.cards.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledList(
    state: MainUiState,
    container: AppContainer,
    focusState: MainFocusState,
    onOpenDetail: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = remember(state.installedApps, state.manifest, state.includeSystemApps) {
        AppCatalog.installedEntries(state.manifest, state.installedApps, state.includeSystemApps)
    }
    val rows = remember(entries) {
        entries.chunked(2).mapIndexed { index, chunk ->
            ContentRow(header = 0, cards = chunk, rowIndex = index)
        }
    }

    if (entries.isEmpty()) {
        EmptyText(stringResource(R.string.state_installed_empty))
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(rows, key = { it.rowIndex }) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                row.cards.forEach { entry ->
                    AppCardSlot {
                        AppCardRow(
                            entry = entry,
                            container = container,
                            focusState = focusState,
                            rowIndex = row.rowIndex,
                            onClick = { onOpenDetail(entry) },
                        )
                    }
                }
                if (row.cards.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NoSourceHint() {
    Column(modifier = Modifier.padding(top = 40.dp)) {
        Text(
            text = stringResource(R.string.manifest_none_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.manifest_none_body),
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppCardRow(
    entry: AppEntry,
    container: AppContainer,
    focusState: MainFocusState,
    rowIndex: Int,
    onClick: () -> Unit,
) {
    val requester = remember(entry.packageName) { FocusRequester() }
    val installedDrawable = entry.installed?.icon
    val icon = rememberAppIcon(entry, installedDrawable, container.iconCache)

    // Dang ky the ngay khi san (truoc lan compose dau tien) de phim `Down` tu vung hanh dong
    // co the goi `requestFocus()` ngay ca khi the chua tung duoc bam vao.
    DisposableEffect(entry.packageName) {
        focusState.register(entry.packageName, requester)
        onDispose { focusState.unregister(entry.packageName) }
    }
    AppCard(
        entry = entry,
        icon = icon,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    focusState.onFocusCard(entry.packageName, rowIndex)
                } else if (focusState.focusedPackage == entry.packageName) {
                    focusState.onFocusLostCard(entry.packageName)
                }
            },
    )
}

@Composable
private fun Header(onRetryManifest: () -> Unit, usingCached: Boolean) {
    val time = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (usingCached) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.manifest_cached),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = time,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Tabs(selected: MainTab, onSelect: (MainTab) -> Unit) {
    TabRow(selectedTabIndex = selected.ordinal, containerColor = MaterialTheme.colorScheme.background) {
        Tab(
            selected = selected == MainTab.UPDATES,
            onClick = { onSelect(MainTab.UPDATES) },
            onFocus = { },
        ) {
            Text(
                text = stringResource(R.string.tab_updates),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Tab(
            selected = selected == MainTab.INSTALLED,
            onClick = { onSelect(MainTab.INSTALLED) },
            onFocus = { },
        ) {
            Text(
                text = stringResource(R.string.tab_installed),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LoadingText() {
    Text(
        text = stringResource(R.string.manifest_loading),
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 40.dp),
    )
}

@Composable
private fun ManifestError(text: String) {
    Column(modifier = Modifier.padding(top = 30.dp)) {
        Text(
            text = stringResource(R.string.manifest_error),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = text,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 40.dp),
    )
}

private fun buildRows(groups: AppCatalog.AppGroups): List<ContentRow> {
    val rows = mutableListOf<ContentRow>()
    var rowIndex = 0

    fun addCards(@androidx.annotation.StringRes headerRes: Int, entries: List<AppEntry>) {
        if (entries.isEmpty()) return
        rows += ContentRow(header = headerRes, cards = emptyList(), rowIndex = rowIndex)
        rowIndex++
        var index = 0
        while (index < entries.size) {
            val chunk = entries.subList(index, minOf(index + 2, entries.size))
            rows += ContentRow(header = 0, cards = chunk, rowIndex = rowIndex)
            rowIndex++
            index += 2
        }
    }

    addCards(R.string.group_has_update, groups.hasUpdate)
    addCards(R.string.group_up_to_date, groups.upToDate)
    addCards(R.string.group_not_installed, groups.notInstalled)
    addCards(R.string.group_unknown, groups.unknown)
    return rows
}
