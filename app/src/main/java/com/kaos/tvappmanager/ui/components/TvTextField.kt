package com.kaos.tvappmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * O nhap lieu cho TV.
 *
 * tv-material khong co `TextField`, va chi con `BasicTextField` la co ban. Day la ban
 * day du dung cho remote: vien de thay, kich thuoc lon, va vien trang ro rang khi nhan
 * focus de nguoi dung thay vi tri con tro.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
) {
    var focused by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 4.dp else 1.dp,
                color = if (focused) Color.White
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .focusable(),
        decorationBox = { inner ->
            if (value.isEmpty() && hint.isNotEmpty()) {
                Text(
                    text = hint,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            inner()
        },
    )
}
