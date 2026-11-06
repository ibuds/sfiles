package io.github.ibuds.sfiles

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun openFile(ctx: Context, f: File) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try { ctx.startActivity(Intent.createChooser(i, null)) } catch (_: Exception) { Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_SHORT).show() }
}

private fun share(ctx: Context, files: List<File>) {
    val uris = ArrayList(files.filter { it.isFile }.map { FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", it) })
    if (uris.isEmpty()) { Toast.makeText(ctx, "Folders can't be shared", Toast.LENGTH_SHORT).show(); return }
    val i = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, null))
}

private sealed interface Dlg {
    data class Details(val e: Entry) : Dlg
    data class Rename(val e: Entry) : Dlg
    object Delete : Dlg
    object NewFolder : Dlg
    data class Pick(val move: Boolean) : Dlg
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    dir: File?, category: Category?,
    onFolder: (File?) -> Unit, onBack: () -> Unit, onSearch: () -> Unit, onSettings: () -> Unit, onVoice: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sort by remember { mutableStateOf(SortBy.Name) }
    var asc by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf(Kind.All) }
    var tick by remember { mutableIntStateOf(0) }
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var dlg by remember { mutableStateOf<Dlg?>(null) }

    val entries by produceState<List<Entry>?>(null, dir, category, sort, asc, filter, tick, Fs.showHidden) {
        value = withContext(Dispatchers.IO) {
            if (category != null) Fs.category(ctx, category) else Fs.list(dir!!, sort, asc, filter)
        }
    }
    val list = entries ?: emptyList()
    val sel = list.filter { it.path in selected }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val paths = remember(list) { list.map { it.path } }

    fun clear() { selectMode = false; selected = emptySet() }
    fun refresh() { clear(); tick++ }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    fun work(block: () -> String?) = scope.launch {
        val msg = withContext(Dispatchers.IO) { try { block() } catch (e: Exception) { e.message ?: "Failed" } }
        if (msg != null) toast(msg)
        refresh()
    }

    BackHandler(selectMode) { clear() }
    LaunchedEffect(dir, category) { clear() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Top bar
            if (selectMode) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.clip(CircleShape).background(Pill).clickable {
                            selected = if (selected.size == list.size) emptySet() else list.map { it.path }.toSet()
                        }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Check(selected.isNotEmpty() && selected.size == list.size)
                        Spacer(Modifier.width(12.dp))
                        Text("${selected.size}", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Cancel", fontSize = 18.sp,
                        modifier = Modifier.clip(CircleShape).background(Pill).clickable { clear() }.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    if (category != null) Text(category.label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    var topMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton({ topMenu = true }) { Icon(Icons.Filled.MoreVert, null) }
                        DropdownMenu(topMenu, { topMenu = false }, shape = RoundedCornerShape(24.dp), containerColor = MenuBg) {
                            DropdownMenuItem({ Text("Edit", fontSize = 17.sp) }, { topMenu = false; selectMode = true })
                            if (dir != null) DropdownMenuItem({ Text("Create folder", fontSize = 17.sp) }, { topMenu = false; dlg = Dlg.NewFolder })
                            DropdownMenuItem({ Text("Settings", fontSize = 17.sp) }, { topMenu = false; onSettings() })
                        }
                    }
                }
            }
            if (dir != null) Breadcrumb(dir, onFolder, Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp))
            // Sort / filter row
            if (category == null) SortRow(filter, { filter = it }, sort, { sort = it }, asc, { asc = !asc })


            if (entries == null) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            else if (list.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No files", color = Muted) }
            else LazyColumn(
                Modifier.fillMaxSize().dragSelect(listState, paths, selected, { selected = it }, { selectMode = true }),
                state = listState, contentPadding = PaddingValues(bottom = 110.dp),
            ) {
                items(list, key = { it.path }) { e ->
                    EntryRow(
                        e, selectMode, e.path in selected,
                        onClick = {
                            if (selectMode) selected = if (e.path in selected) selected - e.path else selected + e.path
                            else if (e.isDir) onFolder(e.file) else openFile(ctx, e.file)
                        },
                        onLongClick = { selectMode = true; selected = selected + e.path },
                        subtitle = if (category != null) e.file.parentFile?.let { Fs.displayPath(it).removePrefix("/Internal storage") .ifEmpty { "/" } } else null, modifier = Modifier.animateItem())
                }
            }
        }

        AnimatedVisibility(
            !selectMode, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(260)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
        ) { SearchPill(onSearch, onVoice, Modifier.navigationBarsPadding().padding(bottom = 12.dp)) }
        AnimatedVisibility(
            selectMode, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(260)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
        ) {
            var more by remember { mutableStateOf(false) }
            Row(
                Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
                    .fillMaxWidth().clip(CircleShape).background(Bar).padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val has = sel.isNotEmpty()
                BarItem(Icons.Outlined.DriveFileMove, "Move", has) { dlg = Dlg.Pick(true) }
                BarItem(Icons.Outlined.ContentCopy, "Copy", has) { dlg = Dlg.Pick(false) }
                BarItem(Icons.Outlined.Share, "Share", has) { share(ctx, sel.map { it.file }) }
                BarItem(Icons.Outlined.Delete, "Delete", has) { dlg = Dlg.Delete }
                Box {
                    BarItem(Icons.Filled.MoreVert, "More", has) { more = true }
                    DropdownMenu(more, { more = false }, shape = RoundedCornerShape(24.dp), containerColor = MenuBg) {
                        if (sel.size == 1) {
                            DropdownMenuItem({ Text("Details", fontSize = 17.sp) }, { more = false; dlg = Dlg.Details(sel[0]) })
                            DropdownMenuItem({ Text("Rename", fontSize = 17.sp) }, { more = false; dlg = Dlg.Rename(sel[0]) })
                        }
                        DropdownMenuItem({ Text("Compress", fontSize = 17.sp) }, {
                            more = false
                            val files = sel.map { it.file }
                            work { val z = Fs.zip(files, files.first().parentFile!!); "Created ${z.name}" }
                        })
                    }
                }
            }
        }
    }

    when (val d = dlg) {
        is Dlg.Details -> DetailsDialog(d.e) { dlg = null }
        is Dlg.Rename -> {
            var name by remember { mutableStateOf(d.e.name) }
            AlertDialog(
                onDismissRequest = { dlg = null }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
                title = { Text("Rename", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        name, { name = it }, singleLine = true,
                        supportingText = { if (name.startsWith(".")) Text("Names starting with a dot are hidden.") },
                    )
                },
                confirmButton = { TextButton({ dlg = null; work { if (Fs.rename(d.e.file, name)) null else "Couldn't rename" } }) { Text("Rename") } },
                dismissButton = { TextButton({ dlg = null }) { Text("Cancel") } },
            )
        }
        Dlg.NewFolder -> {
            var n by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { dlg = null }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
                title = { Text("Create folder", fontWeight = FontWeight.Bold) },
                text = { OutlinedTextField(n, { n = it }, singleLine = true, placeholder = { Text("Folder name") }) },
                confirmButton = { TextButton({ dlg = null; work { if (n.isNotBlank() && File(dir!!, n).mkdirs()) null else "Couldn't create folder" } }) { Text("Create") } },
                dismissButton = { TextButton({ dlg = null }) { Text("Cancel") } },
            )
        }
        Dlg.Delete -> AlertDialog(
            onDismissRequest = { dlg = null }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
            title = { Text(if (sel.any { Fs.isRemovable(it.file) }) "Delete permanently?" else "Move to Trash?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (sel.any { Fs.isRemovable(it.file) }) "Items on a USB drive can't be recovered. SFiles doesn't create a trash folder on removable drives."
                    else "${sel.size} selected item(s) will be moved to the trash.",
                )
            },
            confirmButton = {
                TextButton({
                    dlg = null; val files = sel.map { it.file }
                    work { if (files.all { Fs.trash(it) }) null else "Some items couldn't be deleted" }
                }) { Text(if (sel.any { Fs.isRemovable(it.file) }) "Delete" else "Move to Trash") }
            },
            dismissButton = { TextButton({ dlg = null }) { Text("Cancel") } },
        )
        is Dlg.Pick -> FolderPicker(
            start = dir ?: Fs.root, actionLabel = if (d.move) "Move here" else "Copy here",
            onDismiss = { dlg = null },
            onPicked = { target ->
                dlg = null; val files = sel.map { it.file }
                work {
                    val ok = files.all { if (d.move) Fs.move(it, target) else Fs.copy(it, target) }
                    if (ok) null else "Some items couldn't be ${if (d.move) "moved" else "copied"}"
                }
            },
        )
        null -> {}
    }
}

@Composable
private fun SortRow(filter: Kind, onFilter: (Kind) -> Unit, sort: SortBy, onSort: (SortBy) -> Unit, asc: Boolean, onAsc: () -> Unit) {
    var fm by remember { mutableStateOf(false) }
    var sm by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Row(Modifier.clickable { fm = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(filter.label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(fm, { fm = false }, shape = RoundedCornerShape(24.dp), containerColor = MenuBg) {
                Kind.values().filter { it != Kind.Other }.forEach { k -> DropdownMenuItem({ Text(k.label) }, { fm = false; onFilter(k) }) }
            }
        }
        Spacer(Modifier.weight(1f))
        Box {
            Row(Modifier.clickable { sm = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.Sort, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(sort.label, fontSize = 15.sp)
            }
            DropdownMenu(sm, { sm = false }, shape = RoundedCornerShape(24.dp), containerColor = MenuBg) {
                SortBy.values().forEach { s -> DropdownMenuItem({ Text(s.label) }, { sm = false; onSort(s) }) }
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(1.dp).height(18.dp).background(Divider))
        Spacer(Modifier.width(10.dp))
        Icon(if (asc) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward, null, modifier = Modifier.size(22.dp).clickable { onAsc() })
    }
}

@Composable
private fun BarItem(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val c = if (enabled) MaterialTheme.colorScheme.onSurface else Color(0xFF666666)
        Icon(icon, null, tint = c, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 12.sp, color = c)
    }
}

@Composable
private fun DetailsDialog(e: Entry, onDismiss: () -> Unit) {
    val info by produceState<Triple<Long, Long, String?>?>(null, e) {
        value = withContext(Dispatchers.IO) {
            val c = if (e.isDir) Fs.contents(e.file) else null
            Triple(Fs.treeSize(e.file), Fs.createdAt(e.file),
                c?.let { (f, d) -> buildString { append("$f file${if (f == 1) "" else "s"}"); if (d > 0) append(", $d folder${if (d == 1) "" else "s"}") } })
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Sheet, shape = RoundedCornerShape(32.dp),
        title = { Text("Details", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (e.isDir) FolderIcon(e.file, Modifier.scale(0.9f)) else Thumb(e.file)
                    Spacer(Modifier.width(16.dp)); Text(e.name, fontSize = 17.sp)
                }
                Field("Size", info?.first?.let(::formatSize) ?: "Calculating…")
                Field("Last modified", formatDate(e.modified))
                Field("Added to device", info?.second?.let(::formatDate) ?: "…")
                info?.third?.let { Field("Contains", it) }
                Field("Path", Fs.displayPath(e.file))
            }
        },
        confirmButton = { TextButton(onDismiss, Modifier.fillMaxWidth()) { Text("OK", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) } },
    )
}

@Composable
private fun Field(label: String, value: String) = Column {
    Text(label, fontSize = 14.sp, color = Muted)
    Text(value, fontSize = 17.sp)
}

private fun Modifier.scale(s: Float) = this.then(Modifier.graphicsLayer(scaleX = s, scaleY = s))
