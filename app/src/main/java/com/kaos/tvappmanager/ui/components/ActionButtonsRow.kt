package com.kaos.tvappmanager.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kaos.tvappmanager.R

private val BUTTON_PADDING = PaddingValues(horizontal = 26.dp, vertical = 14.dp)

/**
 * Vung hanh dong chinh: 2 nut "Cap nhat tat ca" va "Cai tat ca".
 *
 * Khi hang doi dang chay, nut ben trai doi thanh "Dang xu ly (x/y)" va mo man hinh
 * tien do (spec section 7). Khi so luong bang 0, nut bi vo hieu hoa va KHONG nhan
 * focus — nguoi dung khong cham phai phan tu khong lam duoc gi.
 */
@Composable
fun ActionButtonsRow(
    updateCount: Int,
    installCount: Int,
    queueRunning: Boolean,
    queueDone: Int,
    queueTotal: Int,
    hasActiveSource: Boolean,
    canInstall: Boolean,
    focusState: MainFocusState,
    onQueueStateClick: () -> Unit,
    onUpdateAll: () -> Unit,
    onInstallAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (queueRunning) {
            QueueStateButton(
                done = queueDone,
                total = queueTotal,
                focusRequester = focusState.updateAllRequester,
                onClick = onQueueStateClick,
                onFocus = { focusState.onFocusAction(MainFocusState.ACTION_UPDATE_ALL) },
            )
        } else {
            ActionButton(
                label = stringResource(R.string.action_update_all, updateCount),
                enabled = hasActiveSource && canInstall,
                focusRequester = focusState.updateAllRequester,
                onFocus = { focusState.onFocusAction(MainFocusState.ACTION_UPDATE_ALL) },
                onClick = onUpdateAll,
            )
        }

        Spacer(Modifier.width(20.dp))

        ActionButton(
            label = stringResource(R.string.action_install_all, installCount),
            enabled = !queueRunning && hasActiveSource && canInstall,
            focusRequester = focusState.installAllRequester,
            onFocus = { focusState.onFocusAction(MainFocusState.ACTION_INSTALL_ALL) },
            onClick = onInstallAll,
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
        ),
        contentPadding = BUTTON_PADDING,
        modifier = Modifier
            .then(if (enabled) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { state -> if (state.isFocused && enabled) onFocus() },
    ) {
        Text(
            text = label,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun QueueStateButton(
    done: Int,
    total: Int,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onFocus: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primary,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
        ),
        contentPadding = BUTTON_PADDING,
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state -> if (state.isFocused) onFocus() },
    ) {
        Text(
            text = stringResource(R.string.action_progress, done, total),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
