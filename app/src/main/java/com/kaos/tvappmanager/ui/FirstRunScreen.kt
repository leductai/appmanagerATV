package com.kaos.tvappmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kaos.tvappmanager.R
import com.kaos.tvappmanager.ui.components.TvButton

/**
 * Man hinh luong du luong dau tien: hien thi loi nhac va mo man hinh cai quyen cua he thong.
 * Neu bo qua, app van dung duoc — chi cac nut cai va cap nhat se bi vo hieu hoa.
 */
@Composable
fun FirstRunScreen(
    permissionGranted: Boolean,
    onAllow: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 120.dp, vertical = 90.dp),
    ) {
        Text(
            text = stringResource(R.string.permission_title),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(
                if (permissionGranted) R.string.permission_already_granted
                else R.string.permission_body
            ),
            fontSize = 22.sp,
            lineHeight = 34.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.75f),
        )
        Spacer(Modifier.height(48.dp))
        if (permissionGranted) {
            TvButton(label = stringResource(R.string.btn_back), onClick = onLater, emphasize = true, requestFocusOnCompose = true)
        } else {
            TvButton(label = stringResource(R.string.permission_allow), onClick = onAllow, emphasize = true, requestFocusOnCompose = true)
            Spacer(Modifier.height(18.dp))
            TvButton(label = stringResource(R.string.permission_grant_later), onClick = onLater)
        }
    }
}
