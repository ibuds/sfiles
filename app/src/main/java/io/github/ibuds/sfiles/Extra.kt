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
private fun TopBack(title: String?, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        if (title != null) Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun HomeCrumb(label: String, onHome: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(CircleShape).background(Pill).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Home, null, tint = Muted, modifier = Modifier.size(22.dp).clickable { onHome() })
        Icon(Icons.Outlined.ChevronRight, null, tint = Muted, modifier = Modifier.padding(horizontal = 6.dp).size(18.dp))
        Text(label, fontSize = 15.sp, color = Accent, fontWeight = FontWeight.Bold)
    }
}

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

    fun run(block: () -> Unit) = scope.launch {
        withContext(Dispatchers.IO) { block() }
        selected = emptySet(); tick++
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBack("Trash", onBack) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                    DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(24.dp), containerColor = MenuBg) {
                        DropdownMenuItem({ Text("Empty trash", fontSize = 17.sp) }, { menu = false; emptyAll = true })
                    }
                }
            }
            HomeCrumb("Trash", onBack)
            if (items != null && list.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                    Text("No files or folders", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Any files or folders you delete will stay in the Trash for 30 days before they're deleted forever.",
                        fontSize = 15.sp, color = Muted, textAlign = TextAlign.Center,
                    )
                }
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
            Row(
                Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
                    .fillMaxWidth().clip(CircleShape).background(Bar).padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TrashAction(Icons.Outlined.Restore, "Restore") { val s = sel; run { s.forEach { Fs.restore(it) } } }
                TrashAction(Icons.Outlined.Delete, "Delete") { confirm = true }
            }
        }
    }
    if (emptyAll) AlertDialog(
        onDismissRequest = { emptyAll = false }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
        title = { Text("Empty trash?", fontWeight = FontWeight.Bold) },
        text = { Text("Everything in the trash will be deleted permanently.") },
        confirmButton = { TextButton({ emptyAll = false; run { Fs.trashItems().forEach { Fs.deleteForever(it) } } }) { Text("Empty") } },
        dismissButton = { TextButton({ emptyAll = false }) { Text("Cancel") } },
    )
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
        title = { Text("Delete forever?", fontWeight = FontWeight.Bold) },
        text = { Text("${sel.size} item(s) will be deleted permanently.") },
        confirmButton = { TextButton({ confirm = false; val s = sel; run { s.forEach { Fs.deleteForever(it) } } }) { Text("Delete") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
    )
}

@Composable
private fun TrashAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(24.dp)); Text(label, fontSize = 12.sp)
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var themeDialog by remember { mutableStateOf(false) }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0" }
    val modes = listOf("Follow device settings", "Light", "Dark")
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBack("SFiles settings", onBack)
        Text("Appearance", fontSize = 14.sp, color = Muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp))
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Card)) {
            Column(Modifier.fillMaxWidth().clickable { themeDialog = true }.padding(horizontal = 24.dp, vertical = 18.dp)) {
                Text("Theme", fontSize = 18.sp)
                Text(modes[Fs.themeMode], fontSize = 13.sp, color = Accent)
            }
        }
        Text("File management", fontSize = 14.sp, color = Muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 8.dp))
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Card)
                .clickable { Fs.updateShowHidden(!Fs.showHidden) }.padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Show hidden files", fontSize = 18.sp)
                Text("Files and folders starting with a dot", fontSize = 13.sp, color = Muted)
            }
            Switch(Fs.showHidden, { Fs.updateShowHidden(it) })
        }
        Text("About", fontSize = 14.sp, color = Muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 8.dp))
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
        confirmButton = { TextButton({ themeDialog = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } },
    )
}
