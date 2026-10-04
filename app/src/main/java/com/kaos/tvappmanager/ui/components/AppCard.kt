package com.kaos.tvappmanager.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kaos.tvappmanager.R
import com.kaos.tvappmanager.data.apps.AppEntry

private val ICON_DP = 70.dp

/**
 * The uc dung dung: icon 70dp, ten app, version va nhan trang thai bang CHU.
 * Trang thai luon co chu ("Co ban moi", "Da moi nhat", "Chua cai") — khong bao gio
 * chi dua vao mau sac, vi nguoi lon tuoi va nguoi kim khac do deu can doc chu.
 */
@Composable
fun AppCard(
    entry: AppEntry,
    icon: ImageBitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        scale = CardDefaults.scale(focusedScale = 1.06f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(icon = icon, name = entry.name)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = versionLine(entry),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                StatusBadge(entry.status)
            }
        }
    }
}

/** Icon app: dung icon tu manifest neu co, icon da cai neu app dang ton tai,
 *  va icon mac dinh cua TV App Manager khi khong co gi. */
@Composable
fun AppIcon(icon: ImageBitmap?, name: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(ICON_DP)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            icon != null -> Image(
                bitmap = icon,
                contentDescription = stringResource(R.string.cd_app_icon, name),
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit,
            )

            else -> Image(
                painter = painterResource(R.drawable.ic_app_default),
                contentDescription = stringResource(R.string.cd_default_icon),
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
fun StatusBadge(status: AppEntry.Status, modifier: Modifier = Modifier) {
    val label = when (status) {
        AppEntry.Status.HAS_UPDATE -> stringResource(R.string.badge_has_new)
        AppEntry.Status.UP_TO_DATE -> stringResource(R.string.badge_up_to_date)
        AppEntry.Status.NOT_INSTALLED -> stringResource(R.string.badge_not_installed)
        AppEntry.Status.UNKNOWN -> stringResource(R.string.badge_unknown)
        AppEntry.Status.INSTALLED_ONLY -> stringResource(R.string.badge_installed_only)
    }
    val color = when (status) {
        AppEntry.Status.HAS_UPDATE -> MaterialTheme.colorScheme.primary
        AppEntry.Status.NOT_INSTALLED -> MaterialTheme.colorScheme.secondary
        AppEntry.Status.UP_TO_DATE -> MaterialTheme.colorScheme.onSurfaceVariant
        AppEntry.Status.UNKNOWN -> MaterialTheme.colorScheme.secondary
        AppEntry.Status.INSTALLED_ONLY -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

private fun versionLine(entry: AppEntry): String {
    val installed = entry.installedVersion
    val target = entry.manifestVersion
    return when {
        installed != null && target != null -> "$installed → $target"
        installed != null -> installed
        target != null -> target
        else -> entry.packageName
    }
}

/** The rong co dinh de moi the cung kich thuoc — dung de lam luoi hai cot. */
@Composable
fun AppCardSlot(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.aspectRatio(2.6f)) { content() }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(top = 20.dp, bottom = 10.dp),
    )
}

val StatusAccent = Color(0xFF12B76A)
val StatusMuted = Color(0xFF8AA4B8)
