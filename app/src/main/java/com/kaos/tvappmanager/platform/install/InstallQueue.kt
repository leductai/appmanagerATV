package com.kaos.tvappmanager.platform.install

import com.kaos.tvappmanager.core.AppLogger
import com.kaos.tvappmanager.data.manifest.ManifestApp
import com.kaos.tvappmanager.platform.PlatformError
import com.kaos.tvappmanager.platform.download.ApkDownloader
import com.kaos.tvappmanager.platform.permission.InstallPermission
import com.kaos.tvappmanager.platform.verify.ApkVerifier
import com.kaos.tvappmanager.platform.verify.VerifyResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

data class QueueItemState(
    val manifestApp: ManifestApp,
    val state: State = State.PENDING,
    val progressPercent: Int = 0,
    val error: PlatformError? = null,
) {
    val packageName: String get() = manifestApp.packageName

    enum class State { PENDING, DOWNLOADING, VERIFYING, AWAITING_CONFIRM, INSTALLING, SUCCESS, FAILED, CANCELLED }

    val isTerminal: Boolean
        get() = state == State.SUCCESS || state == State.FAILED || state == State.CANCELLED
}

data class QueueState(
    val items: List<QueueItemState> = emptyList(),
    val running: Boolean = false,
    val finished: Boolean = false,
    val cancelRequested: Boolean = false,
) {
    val total: Int get() = items.size
    val finishedCount: Int get() = items.count { it.isTerminal }
    val currentIndex: Int get() = items.indexOfFirst { !it.isTerminal }
    val successCount: Int get() = items.count { it.state == QueueItemState.State.SUCCESS }
    val failedCount: Int get() = items.count { it.state == QueueItemState.State.FAILED }
    val skippedCount: Int get() = items.count { it.state == QueueItemState.State.CANCELLED }
}

/**
 * Hang doi cai dat chay TUAN TU, khong song song.
 *
 * Ly do khong chay song song: he thong Android chi cho phep mot hop thoai xac nhan
 * cai dat tai mot thoi diem. Commit session moi chi sau khi nhan ket qua session truoc,
 * nguoi dung moi khong bi gap cac hop thoai chong len nhau.
 *
 * Mot muc loi khong lam dung ca hang doi — no duoc ghi nhan va chuyen muc sau.
 */
class InstallQueue(
    private val scope: CoroutineScope,
    private val downloader: ApkDownloader,
    private val verifier: ApkVerifier,
    private val runner: PackageInstallerRunner,
    private val permission: InstallPermission,
    private val autoDeleteApk: () -> Boolean,
    private val logger: AppLogger,
) {
    private val _state = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = _state.asStateFlow()

    private val outcomes = Channel<InstallOutcome>(Channel.BUFFERED)
    private var job: Job? = null

    init {
        InstallStatusReceiver.handler = { outcome -> outcomes.trySend(outcome) }
    }

    fun start(apps: List<ManifestApp>) {
        if (apps.isEmpty()) return
        if (_state.value.running) return
        job?.cancel()
        _state.value = QueueState(
            items = apps.map { QueueItemState(it) },
            running = true,
        )
        job = scope.launch { run(apps) }
    }

    fun cancel() {
        _state.value = _state.value.copy(cancelRequested = true)
    }

    fun reset() {
        job?.cancel()
        job = null
        _state.value = QueueState()
    }

    /** Chay lai cac muc da loi, giữ nguyen thu tu cua danh sach loi. */
    fun retryFailed() {
        val current = _state.value
        if (current.running) return
        val failed = current.items.filter { it.state == QueueItemState.State.FAILED }
        if (failed.isEmpty()) return
        start(failed.map { it.manifestApp })
    }

    private suspend fun run(apps: List<ManifestApp>) {
        for (app in apps) {
            if (_state.value.cancelRequested) break

            updateItem(app.packageName) { it.copy(state = QueueItemState.State.DOWNLOADING, progressPercent = 0) }

            if (!permission.has()) {
                fail(app.packageName, PlatformError.PERMISSION)
                continue
            }

            val target = File(runner.tempApkDir(), "${app.packageName}-${app.version}.apk")
            target.parentFile?.mkdirs()
            target.delete()

            val download = downloader.download(
                url = app.apkUrl,
                target = target,
                onProgress = { read, total ->
                    if (total > 0) {
                        val percent = ((read * 100) / total).toInt()
                        updateItem(app.packageName) {
                            it.copy(
                                state = QueueItemState.State.DOWNLOADING,
                                progressPercent = percent.coerceIn(0, 100)
                            )
                        }
                    }
                },
            )

            val file = download.file
            if (file == null) {
                fail(app.packageName, download.error ?: PlatformError.NETWORK)
                continue
            }

            updateItem(app.packageName) {
                it.copy(state = QueueItemState.State.VERIFYING, progressPercent = 100)
            }

            when (val verification = verifier.verify(file, app)) {
                is VerifyResult.Fail -> {
                    cleanup(app.packageName)
                    fail(app.packageName, verification.error)
                    continue
                }

                is VerifyResult.Ok -> Unit
            }

            if (_state.value.cancelRequested) {
                cleanup(app.packageName)
                cancelItem(app.packageName)
                continue
            }

            updateItem(app.packageName) { it.copy(state = QueueItemState.State.AWAITING_CONFIRM) }

            when (val commit = runner.commit(file, app)) {
                is CommitResult.Failed -> {
                    cleanup(app.packageName)
                    fail(app.packageName, commit.error)
                    continue
                }

                CommitResult.Submitted -> Unit
            }

            // Cho he thong mo hop thoai xac nhan va tra ket qua ve receiver.
            val outcome = awaitOutcomeFor(app.packageName)

            cleanup(app.packageName)
            if (outcome.isSuccess) {
                updateItem(app.packageName) { it.copy(state = QueueItemState.State.SUCCESS) }
                onInstalled?.invoke(app.packageName)
            } else {
                fail(app.packageName, outcome.error ?: PlatformError.UNKNOWN)
            }
        }

        // Nhung muc chua toi khi bi huy: danh dau bo qua.
        val remaining = _state.value.items.filter { !it.isTerminal }
        remaining.forEach { cancelItem(it.packageName) }

        _state.value = _state.value.copy(running = false, finished = true)
    }

    /** Xoa file APK tam; theo mac dinh xoa ngay sau khi xu ly xong muc do. */
    private suspend fun cleanup(packageName: String) {
        if (!autoDeleteApk()) return
        withContext(Dispatchers.IO) {
            runner.tempApkDir().listFiles()
                ?.filter { it.name.startsWith("$packageName-") }
                ?.forEach { runCatching { it.delete() } }
        }
    }

    /**
     * Cho ket qua dung cho goi nay. Neu nhan duoc ket qua cua session khac thi bo qua
     * de khong lam nham ket qua giua cac phien cai dat lien tiec.
     */
    private suspend fun awaitOutcomeFor(packageName: String): InstallOutcome {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(1L)
            val outcome = withTimeoutOrNull(remaining) { outcomes.receive() }
                ?: return InstallOutcome(
                    packageName = packageName,
                    sessionId = -1,
                    status = android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED,
                    statusMessage = "Khong nhan duoc ket qua cai dat he thong.",
                    conflictingPackage = null,
                )
            if (outcome.packageName == null || outcome.packageName == packageName) return outcome
            logger.log(TAG, "bo qua ket qua cho ${outcome.packageName}, dang cho $packageName")
        }
        return InstallOutcome(
            packageName = packageName,
            sessionId = -1,
            status = android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED,
            statusMessage = "Het thoi gian cho ket qua cai dat.",
            conflictingPackage = null,
        )
    }

    private fun updateItem(packageName: String, transform: (QueueItemState) -> QueueItemState) {
        _state.value = _state.value.copy(
            items = _state.value.items.map { if (it.packageName == packageName) transform(it) else it }
        )
    }

    private fun fail(packageName: String, error: PlatformError) {
        logger.log(TAG, "muc $packageName that bai: $error")
        updateItem(packageName) { it.copy(state = QueueItemState.State.FAILED, error = error) }
    }

    private fun cancelItem(packageName: String) {
        updateItem(packageName) { it.copy(state = QueueItemState.State.CANCELLED) }
    }

    /** Khi mot app cai xong, man hinh chinh can so lai danh sach. */
    var onInstalled: ((String) -> Unit)? = null

    private companion object {
        const val TAG = "InstallQueue"

        /**
         * Khong doi vo han: neu he thong khong tra ket qua (nguoi dung de quay tro app,
         * hop thoai mat), muc nay co the khong bao gio xong va chan ca hang doi.
         */
        const val AWAIT_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
