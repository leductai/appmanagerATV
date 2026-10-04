package com.kaos.tvappmanager.platform

import com.kaos.tvappmanager.R

/** Loai loi xay ra voi mot muc trong hang doi, dung chung cho toan bo app. */
enum class PlatformError(val textRes: Int) {
    CHECKSUM(R.string.queue_error_checksum),
    PACKAGE_MISMATCH(R.string.queue_error_package),
    VERSION_MISMATCH(R.string.queue_error_version),
    NETWORK(R.string.queue_error_network),
    VERIFY(R.string.queue_error_verify_apk),
    STORAGE(R.string.queue_error_storage),
    SIGNATURE(R.string.queue_error_signature),
    PERMISSION(R.string.queue_error_permission),
    CANCELLED(R.string.queue_error_user_cancelled),
    UNKNOWN(R.string.queue_error_generic);

    companion object {
        /**
         * Goi boi PackageInstaller. Boi vi khac nhau goi loi khac nhau, chinh la khi
         * nguoi dung can biet ro rang rang truong hop loi chu ky co the dung de go.
         */
        fun fromInstallMessage(message: String?): PlatformError {
            val value = message.orEmpty().lowercase()
            return when {
                value.contains("signature") || value.contains("inconsistent") -> SIGNATURE
                value.contains("conflict") -> SIGNATURE
                value.contains("space") -> STORAGE
                value.contains("abort") -> CANCELLED
                else -> UNKNOWN
            }
        }
    }
}
