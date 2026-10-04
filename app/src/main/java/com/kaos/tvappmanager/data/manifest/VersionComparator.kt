package com.kaos.tvappmanager.data.manifest

/**
 * So sanh hai chuoi version theo chinh sach cua manifest.
 *
 * `semver`: bo tien to `v`, tach cac doan theo `.` va `-`, so tung doan.
 * Doan nao la so nguyen thi so bang so, doan con lai so bang chuoi.
 * Vi du `1.5.0` > `1.4.12`. Khong tach duoc thi ket qua la UNKNOWN.
 *
 * `raw`: so truc tiep chuoi versionCode (truoc do, khi manifest chi cung cap versionName
 * dang nhu `25.04.06.B` thi thu tu so sanh do chinh la thu tu versionCode).
 */
object VersionComparator {

    enum class Order { OLDER, SAME, NEWER, UNKNOWN }

    /** So sanh hai version cua cung mot manifest theo `versionMode`. */
    fun compare(manifestVersion: String, installedVersion: String?, mode: String): Order {
        if (installedVersion == null) return Order.NEWER
        return when (mode) {
            ManifestApp.VERSION_MODE_RAW -> compareRaw(manifestVersion, installedVersion)
            else -> compareSemver(manifestVersion, installedVersion)
        }
    }

    fun isNewer(manifestVersion: String, installedVersion: String?, mode: String): Boolean =
        compare(manifestVersion, installedVersion, mode) == Order.NEWER

    private fun compareRaw(a: String, b: String): Order {
        val na = a.trim()
        val nb = b.trim()
        // Version dang chuoi nhu `25.04.06.B`: so tung doan de sap xep dung thu tu phat hanh.
        val pa = splitSegments(na)
        val pb = splitSegments(nb)
        if (pa.size == 1 && pb.size == 1) {
            val la = pa[0].toLongOrNull()
            val lb = pb[0].toLongOrNull()
            if (la != null && lb != null) return la.compareTo(lb).toOrder()
        }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val sa = pa.getOrNull(i) ?: "0"
            val sb = pb.getOrNull(i) ?: "0"
            val cmp = compareSegment(sa, sb)
            if (cmp != 0) return cmp.toOrder()
        }
        return Order.SAME
    }

    private fun compareSemver(a: String, b: String): Order {
        val rawA = a.trim()
        val rawB = b.trim()
        if (rawA == rawB) return Order.SAME
        val pa = splitSegments(rawA.removePrefix("v").removePrefix("V"))
        val pb = splitSegments(rawB.removePrefix("v").removePrefix("V"))
        if (pa.isEmpty() || pb.isEmpty()) return Order.UNKNOWN
        // Mot ben khong co doan nao la so nguyen thi khong the doc theo semver duoc.
        if (pa.none { it.toLongOrNull() != null } || pb.none { it.toLongOrNull() != null }) {
            return Order.UNKNOWN
        }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val sa = pa.getOrNull(i) ?: "0"
            val sb = pb.getOrNull(i) ?: "0"
            val cmp = compareSegment(sa, sb)
            if (cmp != 0) return cmp.toOrder()
        }
        return Order.SAME
    }

    private fun splitSegments(version: String): List<String> =
        version.trim().split('.', '-', '_').filter { it.isNotEmpty() }

    private fun compareSegment(a: String, b: String): Int {
        val na = a.toLongOrNull()
        val nb = b.toLongOrNull()
        return when {
            na != null && nb != null -> na.compareTo(nb)
            na != null -> 1
            nb != null -> -1
            else -> a.compareTo(b, ignoreCase = false)
        }
    }

    private fun Int.toOrder(): Order = when {
        this < 0 -> Order.OLDER
        this > 0 -> Order.NEWER
        else -> Order.SAME
    }
}
