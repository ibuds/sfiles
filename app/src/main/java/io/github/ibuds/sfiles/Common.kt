package io.github.ibuds.sfiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ChevronRight
import android.content.Context
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Shared building blocks, so every screen uses identical sizes, spacing and colours.

/** One shadow depth for everything that floats: menus, the search pill, the action bar, the progress pill. */
val ShadowElev = 8.dp

/** Dropdown with the app's shape, colour, shadow and comfortable spacing. */
@Composable
fun AppMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) =
    DropdownMenu(
        expanded, onDismiss, modifier = Modifier.widthIn(min = 220.dp), shape = RoundedCornerShape(24.dp), containerColor = MenuBg,
        tonalElevation = 0.dp, shadowElevation = ShadowElev, content = content,
    )

/** Back arrow + title + optional trailing actions. Icons sit 20dp from the screen edge. */
@Composable
fun ScreenHeader(title: String?, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        if (title != null) Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** The three-dot button with its dropdown. [content] receives a function that closes the menu. */
@Composable
fun OverflowMenu(content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }) { Icon(Icons.Filled.MoreVert, "More options") }
        AppMenu(open, { open = false }) { content { open = false } }
    }
}

@Composable
fun MenuItem(label: String, onClick: () -> Unit) =
    androidx.compose.material3.DropdownMenuItem(
        { Text(label, fontSize = 17.sp) }, onClick,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    )

/** Pill showing "home > label" for top-level screens such as Trash and Network storage. */
@Composable
fun HomeCrumb(label: String, onHome: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(CircleShape).background(Pill).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Home, "Home", tint = Muted, modifier = Modifier.size(22.dp).clickable { onHome() })
        Icon(Icons.Outlined.ChevronRight, null, tint = Muted, modifier = Modifier.padding(horizontal = 6.dp).size(18.dp))
        Text(label, fontSize = 15.sp, color = Accent, fontWeight = FontWeight.Bold)
    }
}

/** "3 selected" pill with select-all, and Cancel. */
@Composable
fun SelectionTopBar(count: Int, allSelected: Boolean, onToggleAll: () -> Unit, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(CircleShape).background(Pill).clickable(onClick = onToggleAll).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Check(allSelected)
            Spacer(Modifier.width(12.dp))
            Text("$count", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Cancel", fontSize = 18.sp,
            modifier = Modifier.clip(CircleShape).background(Pill).clickable(onClick = onCancel).padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

/** Floating bottom bar shown while items are selected. */
@Composable
fun ActionBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
            .fillMaxWidth().shadow(ShadowElev, CircleShape).background(Bar).padding(horizontal = 8.dp, vertical = 10.dp),
        content = content,
    )
}

@Composable
fun RowScope.BarAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    // Every action gets an equal-width slot, so the icons are evenly spaced whatever their label length.
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val c = if (enabled) MaterialTheme.colorScheme.onSurface else Muted.copy(alpha = 0.5f)
        Icon(icon, null, tint = c, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 12.sp, color = c)
    }
}

@Composable
fun EmptyState(title: String, body: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text(title, fontSize = 20.sp, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(12.dp))
            Text(body, fontSize = 15.sp, color = Muted, textAlign = TextAlign.Center)
        }
        if (action != null) { Spacer(Modifier.height(24.dp)); action() }
    }
}

fun itemsText(n: Int) = if (n == 1) "1 item" else "$n items"

/** Small grey heading above a group of cards. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Muted, modifier = modifier)

/** Folder path for list subtitles, without the redundant "/Internal storage" prefix. */
fun shortPath(dir: java.io.File): String = Fs.displayPath(dir).removePrefix("/Internal storage").ifEmpty { "/" }

/** Floating card for one running operation: title and percentage, a progress bar, details, then Hide and Cancel below the bar. */
@Composable
fun OpProgressCard(t: OpTask, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().shadow(ShadowElev, RoundedCornerShape(24.dp)).background(Bar).padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
    ) {
        Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
            if (t.total > 0) Text("${t.percent}%", fontSize = 14.sp, color = Muted)
        }
        Spacer(Modifier.height(10.dp))
        val barMod = Modifier.padding(end = 8.dp).fillMaxWidth().height(6.dp).clip(CircleShape)
        if (t.total > 0) androidx.compose.material3.LinearProgressIndicator(
            progress = { t.percent / 100f }, modifier = barMod, color = Accent, trackColor = Chip, drawStopIndicator = {},
        ) else androidx.compose.material3.LinearProgressIndicator(barMod, color = Accent, trackColor = Chip)
        val d = t.detail()
        val eta = t.etaMs()?.let(::formatEta)
        if (d.isNotEmpty() || eta != null) {
            Spacer(Modifier.height(6.dp))
            Text(listOfNotNull(d.ifEmpty { null }, eta).joinToString(" · "), fontSize = 12.sp, color = Muted, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            listOf("Hide" to { t.hidden = true }, "Cancel" to onCancel).forEach { (label, action) ->
                Text(
                    label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Accent,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = action).padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Opens the system storage settings, where Android lets the user eject or format a drive. */
fun openDriveSettings(ctx: Context) {
    val tries = listOf("android.settings.MEMORY_CARD_SETTINGS", android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS, android.provider.Settings.ACTION_SETTINGS)
    for (a in tries) { try { ctx.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) {} }
}

/** Unmounting and formatting are reserved for the system, so SFiles hands over to Settings. */
@Composable
fun DriveSettingsDialog(unmount: Boolean, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
        title = { Text(if (unmount) "Unmount drive" else "Format drive", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                if (unmount) "Android only lets the system Settings app eject drives, so SFiles can't do it directly. Open storage settings and tap the eject button next to the drive."
                else "Android only lets the system Settings app format drives, so SFiles can't do it directly. Formatting erases everything on the drive.",
            )
        },
        confirmButton = { TextButton({ onDismiss(); openDriveSettings(ctx) }) { Text("Open settings") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
