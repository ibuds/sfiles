package io.github.ibuds.sfiles

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TrashScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf(false) }
    var emptyAll by remember { mutableStateOf(false) }
    val items by produceState<List<Fs.TrashItem>?>(null, tick) {
        value = withContext(Dispatchers.IO) { Fs.purgeOld(); Fs.trashItems() }
    }
    val list = items ?: emptyList()
    val sel = list.filter { it.entry.path in selected }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val paths = remember(list) { list.map { it.entry.path } }
    BackHandler(selected.isNotEmpty()) { selected = emptySet() }

    fun op(title: String, done: String, files: List<java.io.File>, block: ((Long) -> Unit) -> Unit) {
        val started = Ops.run(ctx, OpJob(title, done, files, { cb -> block(cb); null }) { msg ->
            if (msg != null) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            selected = emptySet(); tick++
        })
        if (started) selected = emptySet() else Toast.makeText(ctx, "Another operation is still running", Toast.LENGTH_SHORT).show()
    }
    fun forever(items: List<Fs.TrashItem>, cb: (Long) -> Unit) = items.forEach { cb(0); val n = Fs.treeSize(it.entry.file); Fs.deleteForever(it); cb(n) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (selected.isNotEmpty()) {
                SelectionTopBar(selected.size, selected.size == list.size, {
                    selected = if (selected.size == list.size) emptySet() else list.map { it.entry.path }.toSet()
                }) { selected = emptySet() }
            } else {
                ScreenHeader("Trash", onBack) {
                    OverflowMenu { close -> MenuItem("Empty trash") { close(); emptyAll = true } }
                }
            }
            HomeCrumb("Trash", onBack)
            if (items != null && list.isEmpty()) {
                EmptyState("No files or folders", "Any files or folders you delete will stay in the Trash for 30 days before they're deleted forever.")
            } else LazyColumn(
                Modifier.fillMaxSize().dragSelect(listState, paths, selected, { selected = it }, {}),
                state = listState, contentPadding = PaddingValues(top = 8.dp, bottom = 110.dp),
            ) {
                items(list, key = { it.entry.path }) { t ->
                    val p = t.entry.path
                    EntryRow(
                        t.entry, selected.isNotEmpty(), p in selected,
                        onClick = { if (selected.isNotEmpty()) selected = if (p in selected) selected - p else selected + p },
                        onLongClick = { selected = selected + p },
                        subtitle = "Deleted ${formatDate(t.deletedAt)}", modifier = Modifier.animateItem())
                }
            }
        }
        AnimatedVisibility(
            selected.isNotEmpty(), Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(260)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
        ) {
            ActionBar {
                BarAction(Icons.Outlined.Restore, "Restore") {
                    val s = sel
                    op("Restoring ${itemsText(s.size)}", "Restored ${itemsText(s.size)}", s.map { it.entry.file }) { cb -> s.forEach { cb(0); Fs.restore(it, cb) } }
                }
                BarAction(Icons.Outlined.Delete, "Delete") { confirm = true }
            }
        }
    }
    if (emptyAll) AppDialog(
        onDismissRequest = { emptyAll = false }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
        title = { Text("Empty trash?", fontWeight = FontWeight.Bold) },
        text = { Text("Everything in the trash will be deleted permanently.") },
        confirmButton = { TextButton({ emptyAll = false; val all = Fs.trashItems(); op("Emptying trash", "Trash emptied", all.map { it.entry.file }) { cb -> forever(all, cb) } }) { Text("Empty") } },
        dismissButton = { TextButton({ emptyAll = false }) { Text("Cancel") } },
    )
    if (confirm) AppDialog(
        onDismissRequest = { confirm = false }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
        title = { Text("Delete forever?", fontWeight = FontWeight.Bold) },
        text = { Text("${itemsText(sel.size)} will be deleted permanently.") },
        confirmButton = { TextButton({ confirm = false; val s = sel; op("Deleting ${itemsText(s.size)}", "Deleted ${itemsText(s.size)}", s.map { it.entry.file }) { cb -> forever(s, cb) } }) { Text("Delete") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
    )
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var themeDialog by remember { mutableStateOf(false) }
    var fontDialog by remember { mutableStateOf(false) }
    var styleDialog by remember { mutableStateOf(false) }
    val styles = listOf("Classic", "Minimalist")
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0" }
    val modes = listOf("Follow device settings", "Light", "Dark")
    val fonts = listOf("Follow device settings", "Standard Android font")
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        ScreenHeader("Settings", onBack)
        SectionTitle("Appearance", Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp))
        SettingsCard {
            ChoiceRow("Theme", modes[Fs.themeMode]) { themeDialog = true }
            SettingsDivider()
            ChoiceRow("Font", fonts[Fs.fontMode]) { fontDialog = true }
            SettingsDivider()
            ChoiceRow("Style", styles[Fs.style]) { styleDialog = true }
        }
        SectionTitle("File management", Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp))
        SettingsCard {
            ToggleRow("Filter small files", Fs.filterSmall, "Hide files smaller than 30 KB in categories (Images, Docs, ...) and Recent files.") { Fs.updateFilterSmall(it) }
            SettingsDivider()
            ToggleRow("Show hidden files", Fs.showHidden, "To hide an item, select it, then tap More > Hide.") { Fs.updateShowHidden(it) }
            SettingsDivider()
            ToggleRow("Hide file extensions", Fs.hideExt, "Names show without extensions, and renaming keeps the extension so it can't be changed by accident.") { Fs.updateHideExt(it) }
            SettingsDivider()
            ToggleRow("Recently deleted", Fs.useTrash, "Keep deleted files in Trash for 30 days. When off, deleting is permanent.") { Fs.updateUseTrash(it) }
        }
        SectionTitle("Search settings", Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp))
        SettingsCard {
            ToggleRow("Show search history", Fs.historyOn) { Fs.updateHistoryOn(it); if (!it) Fs.removeHistory(null) }
            SettingsDivider()
            ToggleRow("Display recent files in search", Fs.recentInSearch) { Fs.updateRecentInSearch(it) }
        }
        SectionTitle("Notifications", Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp))
        val blocked = !androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        SettingsCard {
            ToggleRow(
                "File operation notifications", Fs.notifications && !blocked,
                if (blocked) "Blocked in system settings. Turn this on to open them." else "Show progress and results of copy, move, compress and delete in the notification shade.",
            ) { on ->
                if (on && androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled().not()) {
                    Fs.updateNotifications(true)
                    try {
                        ctx.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (_: Exception) {}
                } else Fs.updateNotifications(on)
            }
        }
        SectionTitle("About", Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp))
        SettingsCard { ChoiceRow("Version", version, accent = false) {} }
        Spacer(Modifier.height(32.dp))
    }
    if (themeDialog) PickDialog("Theme", modes, Fs.themeMode, { Fs.updateThemeMode(it) }) { themeDialog = false }
    if (styleDialog) PickDialog("Style", styles, Fs.style, { Fs.updateStyle(it) }) { styleDialog = false }
    if (fontDialog) PickDialog("Font", fonts, Fs.fontMode, { Fs.updateFontMode(it) }) { fontDialog = false }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Card), content = content)

@Composable
private fun SettingsDivider() = HorizontalDivider(Modifier.padding(horizontal = 24.dp), thickness = 0.7.dp, color = Divider)

@Composable
private fun ChoiceRow(label: String, value: String, accent: Boolean = true, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 18.dp)) {
        Text(label, fontSize = 17.sp)
        Text(value, fontSize = 14.sp, color = if (accent) Accent else Muted)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 24.dp, vertical = if (sub == null) 14.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, fontSize = 17.sp)
            if (sub != null) Text(sub, fontSize = 14.sp, color = Muted)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun PickDialog(title: String, options: List<String>, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AppDialog(
        onDismissRequest = onDismiss, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEachIndexed { i, label ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(i); onDismiss() }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(current == i, { onPick(i); onDismiss() })
                        Spacer(Modifier.width(8.dp)); Text(label, fontSize = 17.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
