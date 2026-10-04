package com.kaos.tvappmanager.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Gi vien chung cho moi nut tren TV: lon, khoang cach de chon bang remote. */
@Composable
fun TvButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    requestFocusOnCompose: Boolean = false,
    emphasize: Boolean = false,
) {
    val requester = remember { FocusRequester() }
    if (requestFocusOnCompose) {
        androidx.compose.runtime.LaunchedEffect(Unit) { requester.requestFocus() }
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.colors(
            containerColor = if (emphasize) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = modifier
            .then(Modifier.focusRequester(requester)),
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

/** Dung cho nut thu phai, nhau bang khoang trang. */
@Composable
fun SpacerHorizontal() {
    Spacer(Modifier.width(16.dp))
}

@Composable
fun FocusAwareText(text: String) {
    Text(text = text, color = Color.White, fontSize = 16.sp)
}
