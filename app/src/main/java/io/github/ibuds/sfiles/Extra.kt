package io.github.ibuds.sfiles

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    if (emptyAll) AlertDialog(
        onDismissRequest = { emptyAll = false }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
        title = { Text("Empty trash?", fontWeight = FontWeight.Bold) },
        text = { Text("Everything in the trash will be deleted permanently.") },
        confirmButton = { TextButton({ emptyAll = false; val all = Fs.trashItems(); op("Emptying trash", "Trash emptied", all.map { it.entry.file }) { cb -> forever(all, cb) } }) { Text("Empty") } },
        dismissButton = { TextButton({ emptyAll = false }) { Text("Cancel") } },
    )
    if (confirm) AlertDialog(
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
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0" }
    val modes = listOf("Follow device settings", "Light", "Dark")
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Settings", onBack)
        SectionTitle("Appearance", Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp))
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Card)) {
            Column(Modifier.fillMaxWidth().clickable { themeDialog = true }.padding(horizontal = 24.dp, vertical = 18.dp)) {
                Text("Theme", fontSize = 18.sp)
                Text(modes[Fs.themeMode], fontSize = 13.sp, color = Accent)
            }
        }
        SectionTitle("File management", Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp))
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Card)) {
            ToggleRow("Show hidden files", Fs.showHidden) { Fs.updateShowHidden(it) }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp), thickness = 0.7.dp, color = Divider)
            ToggleRow("Hide file extensions", Fs.hideExt) { Fs.updateHideExt(it) }
        }
        SectionTitle("About", Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp))
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Card).padding(horizontal = 24.dp, vertical = 18.dp)) {
            Text("Version", fontSize = 18.sp)
            Text(version, fontSize = 13.sp, color = Muted)
        }
    }
    if (themeDialog) AlertDialog(
        onDismissRequest = { themeDialog = false }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
        title = { Text("Theme", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                modes.forEachIndexed { i, label ->
                    Row(
                        Modifier.fillMaxWidth().clickable { Fs.updateThemeMode(i); themeDialog = false }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(Fs.themeMode == i, { Fs.updateThemeMode(i); themeDialog = false })
                        Spacer(Modifier.width(8.dp)); Text(label, fontSize = 17.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton({ themeDialog = false }) { Text("Cancel") } },
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}
