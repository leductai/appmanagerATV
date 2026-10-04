package com.kaos.tvappmanager.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Nhan chuoi phim so `8-8-0-0` trong vong 4 giay de mo menu cai dat an.
 *
 * Dung `onPreviewKeyEvent` de chay TRUOC khi cac thanh phan nhan phim, va tieu thu
 * su kien — khong con the ra den hop thoai cua he thong. Sai phim hoac het thoi gian
 * thi xoa chuoi nhap im lang, khong hien thong bao (spec section 5).
 */
@Composable
fun Modifier.secretCodeListener(enabled: Boolean, onMatch: () -> Unit): Modifier {
    if (!enabled) return this
    return this.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (digitOf(event)) {
            '8' -> {
                SecretCodeBuffer.push('8', onMatch)
                true
            }

            '0' -> {
                SecretCodeBuffer.push('0', onMatch)
                true
            }

            else -> {
                // Phim khac: hoan toan quen chuoi da nhap, khong thong bao.
                SecretCodeBuffer.reset()
                false
            }
        }
    }
}

/** Lay ky tu so tu phim, ho ca ban phim chuoi va ban phim so tren remote. */
private fun digitOf(event: KeyEvent): Char? {
    val key = event.key
    if (key == Key.Eight || key == Key.NumPad8) return '8'
    if (key == Key.Zero || key == Key.NumPad0) return '0'

    return when (event.nativeKeyEvent?.keyCode) {
        AndroidKeyEvent.KEYCODE_8, AndroidKeyEvent.KEYCODE_NUMPAD_8 -> '8'
        AndroidKeyEvent.KEYCODE_0, AndroidKeyEvent.KEYCODE_NUMPAD_0 -> '0'
        else -> null
    }
}

private object SecretCodeBuffer {
    private const val EXPECTED = "8800"
    private const val TIMEOUT_MS = 4000L

    private val digits = StringBuilder()
    private var lastKeyAt = 0L

    fun push(digit: Char, onMatch: () -> Unit) {
        val now = System.currentTimeMillis()
        // Qua 4 giay tu phim truoc thi het phien nhap truoc.
        if (digits.isNotEmpty() && now - lastKeyAt > TIMEOUT_MS) digits.clear()
        lastKeyAt = now
        digits.append(digit)

        // Van de dung mot chuoi dung: 8800 hoac 8-8-0-0. Tao day hon thi mat dung hon
        // vi nguoi dung khong co ly do nay tiep 8.
        if (digits.length > EXPECTED.length) digits.deleteCharAt(0)

        if (digits.toString() == EXPECTED) {
            reset()
            onMatch()
        }
    }

    fun reset() {
        digits.clear()
    }
}
