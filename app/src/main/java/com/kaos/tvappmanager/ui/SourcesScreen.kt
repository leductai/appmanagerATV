package com.kaos.tvappmanager.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kaos.tvappmanager.R
import com.kaos.tvappmanager.data.source.ManifestSource
import com.kaos.tvappmanager.data.source.SourceStore
import com.kaos.tvappmanager.ui.components.TvButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Quan ly nguon manifest: them, sua, xoa, va chon nguon dang hoat dong.
 * Chi co mot nguon dang hoat dong tai mot thoi diem (spec section 11).
 */
@Composable
fun SourcesScreen(
    sources: List<ManifestSource>,
    activeSourceId: String?,
    onAdd: (name: String, location: String) -> Unit,
    onUpdate: (ManifestSource, name: String, location: String) -> Unit,
    onDelete: (ManifestSource) -> Unit,
    onSelect: (ManifestSource) -> Unit,
    onImportFromDocument: (Uri) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<ManifestSource?>(null) }
    var pending by remember { mutableStateOf<PendingEdit?>(null) }

    val pickManifest = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImportFromDocument) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 80.dp, vertical = 40.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_sources),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))

        if (sources.isEmpty()) {
            Text(
                text = stringResource(R.string.sources_empty),
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(sources, key = { it.id }) { source ->
                SourceRow(
                    source = source,
                    isActive = source.id == activeSourceId,
                    onSelect = { onSelect(source) },
                    onEdit = { pending = PendingEdit(source, source.name, source.location) },
                    onDelete = { onDelete(source) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TvButton(
                label = stringResource(R.string.sources_add),
                onClick = { pending = PendingEdit(null, "", "") },
                requestFocusOnCompose = true,
            )
            Spacer(Modifier.width(14.dp))
            TvButton(
                label = stringResource(R.string.settings_import),
                onClick = { pickManifest.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
            )
            Spacer(Modifier.weight(1f))
            TvButton(label = stringResource(R.string.btn_back), onClick = onBack)
        }
    }

    pending?.let { value ->
        EditSourceDialog(
            initial = value,
            onConfirm = { name, location ->
                val source = value.source
                if (source == null) onAdd(name, location) else onUpdate(source, name, location)
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
}

private data class PendingEdit(
    val source: ManifestSource?,
    val name: String,
    val location: String,
)

@Composable
private fun EditSourceDialog(
    initial: PendingEdit,
    onConfirm: (name: String, location: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var location by remember { mutableStateOf(initial.location) }
    val valid = SourceStore.isValidLocation(location) && name.isNotBlank()
    val errorMessage = remember(location) {
        if (location.isNotBlank() && !SourceStore.isValidLocation(location)) {
            "INVALID_URL"
        } else {
            null
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .width(860.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(36.dp),
        ) {
            Text(
                text = stringResource(
                    if (initial.source == null) R.string.sources_add else R.string.sources_edit
                ),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(22.dp))

            FieldLabel(stringResource(R.string.source_name_label))
            com.kaos.tvappmanager.ui.components.TvTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                hint = "Tên hiển thị",
            )

            FieldLabel(stringResource(R.string.source_url_label))
            com.kaos.tvappmanager.ui.components.TvTextField(
                value = location,
                onValueChange = { location = it },
                modifier = Modifier.fillMaxWidth(),
                hint = "https://…/apps.json",
            )
            if (errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.source_url_invalid),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(26.dp))
            Row {
                TvButton(label = stringResource(R.string.queue_cancel), onClick = onDismiss)
                Spacer(Modifier.weight(1f))
                TvButton(
                    label = stringResource(R.string.queue_start),
                    onClick = { onConfirm(name.trim(), location.trim()) },
                    enabled = valid,
                    emphasize = valid,
                    requestFocusOnCompose = valid,
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun SourceRow(
    source: ManifestSource,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name + if (isActive) "  ✓" else "",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = source.location,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Text(
                text = buildString {
                    append(source.location.substringAfter("://").take(48))
                    append(" · ")
                    append(
                        source.lastSyncMs?.let {
                            SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(it))
                        } ?: "chưa đồng bộ"
                    )
                    source.lastError?.let {
                        append(" · ")
                        append(it)
                    }
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(12.dp))
        TvButton(
            label = stringResource(R.string.sources_select),
            onClick = onSelect,
            enabled = !isActive,
        )
        Spacer(Modifier.width(8.dp))
        TvButton(label = stringResource(R.string.sources_edit), onClick = onEdit)
        Spacer(Modifier.width(8.dp))
        TvButton(label = stringResource(R.string.sources_delete), onClick = onDelete)
    }
}
