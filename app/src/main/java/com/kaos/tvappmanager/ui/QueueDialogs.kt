package com.kaos.tvappmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Checkbox
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kaos.tvappmanager.R
import com.kaos.tvappmanager.data.apps.AppEntry
import com.kaos.tvappmanager.platform.install.QueueItemState
import com.kaos.tvappmanager.ui.components.TvButton

/**
 * Hop thoai xac nhan truoc khi chay hang doi.
 *
 * Checkbox mac dinh DUOC chon; khong cho bat dau khi khong con app nao duoc chon.
 * Luon nhac ro Android TV se hoi xac nhan cai dat tung ung dung (spec section 7).
 *
 * Dung `Dialog` cua compose-ui vi tv-material khong cung cap hieu ung TV rieng.
 */
@Composable
fun QueueConfirmDialog(
    kind: SelectionKind,
    entries: List<AppEntry>,
    selection: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = modifier
                .width(900.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(36.dp),
        ) {
            Text(
                text = stringResource(
                    if (kind == SelectionKind.UPDATE) R.string.queue_confirm_update_title
                    else R.string.queue_confirm_install_title,
                    selection.size,
                ),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.queue_confirm_notice),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(entries, key = { it.packageName }) { entry ->
                    SelectionRow(
                        entry = entry,
                        checked = entry.packageName in selection,
                        onToggle = { onToggle(entry.packageName) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TvButton(label = stringResource(R.string.queue_cancel), onClick = onCancel)
                Spacer(Modifier.width(16.dp))
                TvButton(
                    label = stringResource(R.string.select_all),
                    onClick = onSelectAll,
                )
                Spacer(Modifier.weight(1f))
                TvButton(
                    label = stringResource(R.string.queue_start),
                    onClick = onStart,
                    enabled = selection.isNotEmpty(),
                    emphasize = true,
                    requestFocusOnCompose = true,
                )
            }
        }
    }
}

@Composable
private fun SelectionRow(entry: AppEntry, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                fontSize = 19.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = versionLine(entry),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun versionLine(entry: AppEntry): String {
    val installed = entry.installedVersion
    val target = entry.manifestVersion
    return when {
        installed != null && target != null -> "$installed → $target"
        target != null -> target
        else -> entry.packageName
    }
}

/**
 * Man hinh tien do va ket qua hang doi.
 *
 * Mot muc loi khong lam dung hang doi. Cuoi cung hienh bao Thanh cong / Loi / Bo qua
 * kem nut "Thu lai loi" (spec section 7).
 */
@Composable
fun QueueProgressScreen(
    queueState: com.kaos.tvappmanager.platform.install.QueueState,
    onCancel: () -> Unit,
    onRetryFailed: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 90.dp, vertical = 60.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.action_progress, queueState.finishedCount, queueState.total),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(20.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(queueState.items, key = { it.packageName }) { item ->
                    QueueRow(item)
                }
            }

            if (queueState.finished) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.queue_result_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Text(
                        text = "${stringResource(R.string.queue_result_success)}: ${queueState.successCount}",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(20.dp))
                    Text(
                        text = "${stringResource(R.string.queue_result_failed)}: ${queueState.failedCount}",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(20.dp))
                    Text(
                        text = "${stringResource(R.string.queue_result_skipped)}: ${queueState.skippedCount}",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                if (queueState.running) {
                    TvButton(label = stringResource(R.string.queue_stop), onClick = onCancel)
                } else {
                    if (queueState.failedCount > 0) {
                        TvButton(
                            label = stringResource(R.string.queue_retry_failed),
                            onClick = onRetryFailed,
                            requestFocusOnCompose = true,
                        )
                        Spacer(Modifier.width(16.dp))
                    }
                    TvButton(
                        label = stringResource(R.string.queue_close),
                        onClick = onClose,
                        requestFocusOnCompose = queueState.failedCount == 0,
                        emphasize = queueState.failedCount == 0,
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(item: QueueItemState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.manifestApp.name,
            fontSize = 19.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(360.dp),
        )
        Text(
            text = stateLabel(item),
            fontSize = 16.sp,
            color = when (item.state) {
                QueueItemState.State.SUCCESS -> MaterialTheme.colorScheme.primary
                QueueItemState.State.FAILED -> MaterialTheme.colorScheme.error
                QueueItemState.State.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun stateLabel(item: QueueItemState): String = when (item.state) {
    QueueItemState.State.PENDING -> "…"
    QueueItemState.State.DOWNLOADING ->
        stringResource(R.string.queue_state_downloading, item.progressPercent)

    QueueItemState.State.VERIFYING -> stringResource(R.string.queue_state_verifying)
    QueueItemState.State.AWAITING_CONFIRM -> stringResource(R.string.queue_state_awaiting)
    QueueItemState.State.INSTALLING -> stringResource(R.string.queue_state_installing)
    QueueItemState.State.SUCCESS -> stringResource(R.string.queue_result_success)
    QueueItemState.State.FAILED -> item.error?.let { error ->
        androidx.compose.ui.res.stringResource(error.textRes)
    } ?: stringResource(R.string.queue_error_generic)

    QueueItemState.State.CANCELLED -> stringResource(R.string.queue_result_skipped)
}
