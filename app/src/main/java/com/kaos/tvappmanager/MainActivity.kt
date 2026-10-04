package com.kaos.tvappmanager

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kaos.tvappmanager.data.source.LanguagePreference
import com.kaos.tvappmanager.platform.install.SystemIntents
import com.kaos.tvappmanager.ui.AppDetailScreen
import com.kaos.tvappmanager.ui.FirstRunScreen
import com.kaos.tvappmanager.ui.HiddenSettingsScreen
import com.kaos.tvappmanager.ui.MainScreen
import com.kaos.tvappmanager.ui.MainViewModel
import com.kaos.tvappmanager.ui.QueueConfirmDialog
import com.kaos.tvappmanager.ui.QueueProgressScreen
import com.kaos.tvappmanager.ui.Screen
import com.kaos.tvappmanager.ui.SelectionKind
import com.kaos.tvappmanager.ui.SourcesScreen
import com.kaos.tvappmanager.ui.components.MainFocusState
import com.kaos.tvappmanager.ui.components.TvButton
import com.kaos.tvappmanager.ui.theme.TvAppManagerTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as TvAppApplication).container

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(container) as T
        }
    }

    /**
     * Nhan chuoi `8-8-0-0` o cap Activity de khong phan thuoc Compose co node nao
     * dang nhan focus hay khong. Khi nut hanh dong bi vo hieu hoa (chua co nguon),
     * khong phan tu nao nhan focus va `Modifier.onPreviewKeyEvent` se khong con phia
     * duoc nhan su kien nua.
     */
    private val secretCode = StringBuilder()
    private var lastSecretKeyAt = 0L

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && feedSecretCode(event.keyCode)) return true
        return super.dispatchKeyEvent(event)
    }

    /** Tra ve true neu phim da duoc xu ly (khong cho buoc xuong Compose). */
    private fun feedSecretCode(keyCode: Int): Boolean {
        val digit = when (keyCode) {
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> '8'
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> '0'
            else -> null
        }
        if (digit == null) {
            // Phim khac: xoa chuoi nhap IM LANG, khong hien thong bao.
            if (secretCode.isNotEmpty()) secretCode.clear()
            return false
        }
        val now = System.currentTimeMillis()
        if (secretCode.isNotEmpty() && now - lastSecretKeyAt > SECRET_CODE_TIMEOUT_MS) {
            secretCode.clear()
        }
        lastSecretKeyAt = now
        secretCode.append(digit)
        if (secretCode.length > SECRET_CODE.length) secretCode.deleteCharAt(0)
        if (secretCode.toString() == SECRET_CODE) {
            secretCode.clear()
            viewModel.navigate(Screen.HIDDEN_SETTINGS)
            return true
        }
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TvAppManagerTheme {
                AppRoot(
                    viewModel = viewModel,
                    onOpenInstallPermission = { container.installPermission.openSystemSettings(this) },
                    onOpenApp = { SystemIntents.openApp(this, it) },
                    onUninstall = { SystemIntents.uninstall(this, it) },
                    onAppInfo = { SystemIntents.appInfo(this, it) },
                    onExit = { finish() },
                    onKeepScreenOn = { keep -> setKeepScreenOn(keep) },
                )
            }
        }
    }

    /** Giu man hinh sang khi hang doi dang chay — TV rat de tat man hinh giua chung. */
    private fun setKeepScreenOn(keepOn: Boolean) {
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private companion object {
        const val SECRET_CODE = "8800"
        const val SECRET_CODE_TIMEOUT_MS = 4000L
    }
}

@Composable
private fun AppRoot(
    viewModel: MainViewModel,
    onOpenInstallPermission: () -> Unit,
    onOpenApp: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onAppInfo: (String) -> Unit,
    onExit: () -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
) {
    val activity = LocalContext.current as? MainActivity
    val container = (activity?.application as? TvAppApplication)?.container
    if (container == null) {
        Text("Dang khoi dong…")
        return
    }

    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val nav by viewModel.nav.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val focusState = remember { MainFocusState() }
    var showExitConfirm by remember { mutableStateOf(false) }

    // Khi nguoi dung doi ngon ngu trong menu an, tai ngu ngay — khong bat phai khoi lai app.
    val language = ui.settings?.language
    val baseContext = LocalContext.current
    val localizedContext = remember(language, baseContext) {
        localizedContextFor(baseContext, language)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
    ) {
        RootContent(
            ui = ui,
            nav = nav,
            queue = queue,
            focusState = focusState,
            showExitConfirm = showExitConfirm,
            onDismissExit = { showExitConfirm = false },
            onOpenExit = { showExitConfirm = true },
            container = container,
            viewModel = viewModel,
            onOpenInstallPermission = onOpenInstallPermission,
            onOpenApp = onOpenApp,
            onUninstall = onUninstall,
            onAppInfo = onAppInfo,
            onExit = onExit,
            onKeepScreenOn = onKeepScreenOn,
        )
    }
}

/** Tao Context con da ap nguon da chon. Trong `system` thi giu ngon ngu may. */
private fun localizedContextFor(base: Context, language: LanguagePreference?): Context {
    val locale = when (language) {
        LanguagePreference.VI -> Locale("vi")
        LanguagePreference.EN -> Locale("en")
        else -> return base
    }
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    return base.createConfigurationContext(config)
}

@Composable
private fun RootContent(
    ui: com.kaos.tvappmanager.ui.MainUiState,
    nav: com.kaos.tvappmanager.ui.NavigationState,
    queue: com.kaos.tvappmanager.platform.install.QueueState,
    focusState: MainFocusState,
    showExitConfirm: Boolean,
    onDismissExit: () -> Unit,
    onOpenExit: () -> Unit,
    container: com.kaos.tvappmanager.di.AppContainer,
    viewModel: MainViewModel,
    onOpenInstallPermission: () -> Unit,
    onOpenApp: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onAppInfo: (String) -> Unit,
    onExit: () -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
) {
    LaunchedEffect(queue.running) { onKeepScreenOn(queue.running) }

    // Quyen co the vua duoc bat trong Settings; app van chay khi quyen chua co
    // (spec section 3) — khong choi roi het man hinh chi vi thieu quyen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.recheckPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Quay lai man hinh chinh: khoi phuc focus cua the truoc do neu con trong danh sach,
    // con khong thi dau man hinh chinh la "Cap nhat tat ca" (spec section 4 va 5).
    LaunchedEffect(nav.screen, nav.detailPackage) {
        if (nav.screen != Screen.MAIN) return@LaunchedEffect
        val saved = viewModel.focusedPackage
        val requester = saved?.let { focusState.requesterFor(it) }
        if (requester != null) {
            requester.requestFocus()
        } else {
            focusState.updateAllRequester.requestFocus()
        }
    }

    if (nav.screen == Screen.FIRST_RUN) {
        FirstRunScreen(
            permissionGranted = ui.canInstall,
            onAllow = onOpenInstallPermission,
            onLater = { viewModel.dismissFirstRun() },
        )
        return
    }

    BoxRoot {
        when (nav.screen) {
            Screen.HIDDEN_SETTINGS -> {
                BackHandler { viewModel.navigate(Screen.MAIN) }
                val temp = viewModel.tempStats()
                HiddenSettingsScreen(
                    state = ui,
                    permissionGranted = ui.canInstall,
                    wifiOnly = ui.settings?.wifiOnly ?: true,
                    timeoutSeconds = ui.settings?.timeoutSeconds ?: 60,
                    autoDeleteApk = ui.settings?.autoDeleteApk ?: true,
                    language = ui.settings?.language ?: LanguagePreference.SYSTEM,
                    logLines = viewModel.logLines,
                    cacheBytes = viewModel.cacheBytes(),
                    tempFileCount = temp.first,
                    tempFileBytes = temp.second,
                    onWifiOnlyChange = { viewModel.setWifiOnly(it) },
                    onTimeoutChange = { viewModel.setTimeoutSeconds(it) },
                    onAutoDeleteChange = { viewModel.setAutoDeleteApk(it) },
                    onLanguageChange = { viewModel.setLanguage(it) },
                    onOpenSources = { viewModel.navigate(Screen.SOURCES) },
                    onImportManifest = { viewModel.navigate(Screen.SOURCES) },
                    onExportConfig = { viewModel.exportConfig() },
                    onOpenPermission = onOpenInstallPermission,
                    onClearCache = { viewModel.clearCaches() },
                    onCleanTemp = { viewModel.cleanTempFiles() },
                    onClearLog = { viewModel.clearLog() },
                    onExportLog = { viewModel.exportLog() },
                    onBack = { viewModel.navigate(Screen.MAIN) },
                )
            }

            Screen.SOURCES -> {
                BackHandler { viewModel.navigate(Screen.HIDDEN_SETTINGS) }
                SourcesScreen(
                    sources = ui.settings?.sources.orEmpty(),
                    activeSourceId = ui.settings?.activeSource?.id,
                    onAdd = { name, location -> viewModel.addSource(name, location) },
                    onUpdate = { source, name, location ->
                        viewModel.updateSource(source, name, location)
                    },
                    onDelete = { viewModel.deleteSource(it) },
                    onSelect = { viewModel.selectSource(it) },
                    onImportFromDocument = { viewModel.importManifestFrom(it) },
                    onBack = { viewModel.navigate(Screen.HIDDEN_SETTINGS) },
                )
            }

            Screen.PROGRESS -> {
                BackHandler { viewModel.navigate(Screen.MAIN) }
                QueueProgressScreen(
                    queueState = queue,
                    onCancel = { viewModel.cancelQueue() },
                    onRetryFailed = { viewModel.retryFailed() },
                    onClose = {
                        viewModel.resetQueue()
                        viewModel.navigate(Screen.MAIN)
                    },
                )
            }

            Screen.DETAIL -> {
                val entry = nav.detailPackage?.let { viewModel.entryFor(it) }
                if (entry == null) {
                    viewModel.navigate(Screen.MAIN)
                } else {
                    BackHandler { viewModel.navigate(Screen.MAIN) }
                    AppDetailScreen(
                        entry = entry,
                        container = container,
                        canInstall = ui.canInstall,
                        onInstallOrUpdate = { viewModel.openSingle(entry) },
                        onOpen = { onOpenApp(entry.packageName) },
                        onUninstall = { onUninstall(entry.packageName) },
                        onAppInfo = { onAppInfo(entry.packageName) },
                        onBack = { viewModel.navigate(Screen.MAIN) },
                    )
                }
            }

            else -> {
                BackHandler { onOpenExit() }
                MainScreen(
                    state = ui,
                    queue = queue,
                    container = container,
                    focusState = focusState,
                    onOpenDetail = { entry ->
                        viewModel.markFocus(entry.packageName)
                        viewModel.navigate(Screen.DETAIL, entry.packageName)
                    },
                    onQueueStateClick = { viewModel.navigate(Screen.PROGRESS) },
                    onUpdateAll = { viewModel.openSelection(SelectionKind.UPDATE) },
                    onInstallAll = { viewModel.openSelection(SelectionKind.INSTALL) },
                    onOpenHiddenSettings = { viewModel.navigate(Screen.HIDDEN_SETTINGS) },
                    onRetryManifest = { viewModel.refreshManifest() },
                    onRequestExit = { onOpenExit() },
                    onTabChange = { viewModel.setTab(it) },
                )
            }
        }

        ui.pendingSelectionKind?.let { kind ->
            QueueConfirmDialog(
                kind = kind,
                entries = ui.pendingEntries,
                selection = ui.selection,
                onToggle = { viewModel.toggleSelection(it) },
                onSelectAll = { viewModel.setAllSelected(true) },
                onStart = { viewModel.startQueue() },
                onCancel = { viewModel.closeSelection() },
            )
        }

        if (showExitConfirm) {
            ExitConfirmDialog(
                onExit = onExit,
                onStay = onDismissExit,
            )
        }
    }
}

@Composable
private fun BoxRoot(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) { content() }
}

@Composable
private fun ExitConfirmDialog(onExit: () -> Unit, onStay: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onStay,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .width(760.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.exit_confirm_title),
                fontSize = 26.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.padding(top = 24.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                TvButton(
                    label = stringResource(R.string.exit_confirm_yes),
                    onClick = onExit,
                    emphasize = true,
                    requestFocusOnCompose = true,
                )
                TvButton(
                    label = stringResource(R.string.exit_confirm_no),
                    onClick = onStay,
                )
            }
        }
    }
}
