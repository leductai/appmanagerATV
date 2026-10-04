package com.kaos.tvappmanager.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.focus.FocusRequester

/**
 * Trang thai focus hien tai cua man hinh chinh.
 *
 * Du lieu nay duoc cac the va nut cap nhat lai khi focus doan vi tri — cho phep
 * `MainScreen` bat su kien ban phim va quyet dinh di doc hay dung yen, theo bang
 * luat focus (spec section 5).
 */
class MainFocusState {

    /** Hang (0-based) cua the dang nhan focus. -1 khi khong the nao dang nhan focus. */
    var focusedRowIndex by mutableIntStateOf(-1)
        internal set

    /** Dang giu nut hanh dong nao. */
    var focusedAction by mutableIntStateOf(ACTION_NONE)
        internal set

    var focusedPackage by mutableStateOf<String?>(null)
        internal set

    val updateAllRequester = FocusRequester()
    val installAllRequester = FocusRequester()

    private val cardRequesters = mutableStateMapOf<String, FocusRequester>()

    fun requesterFor(packageName: String): FocusRequester? = cardRequesters[packageName]

    /** Dang ky/rut the: goi khi the du compose, huy khi bo di. */
    fun register(packageName: String, requester: FocusRequester) {
        cardRequesters[packageName] = requester
    }

    fun unregister(packageName: String) {
        cardRequesters.remove(packageName)
    }

    fun onFocusCard(packageName: String?, rowIndex: Int) {
        focusedRowIndex = rowIndex
        focusedAction = ACTION_NONE
        focusedPackage = packageName
    }

    fun onFocusAction(action: Int) {
        focusedRowIndex = -1
        focusedAction = action
        focusedPackage = null
    }

    fun onFocusLostCard(packageName: String?) {
        if (focusedPackage == packageName) focusedRowIndex = -1
    }

    companion object {
        const val ACTION_NONE = -1
        const val ACTION_UPDATE_ALL = 0
        const val ACTION_INSTALL_ALL = 1
    }
}
