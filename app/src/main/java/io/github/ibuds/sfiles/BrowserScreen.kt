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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
    if (f.extension.equals("apk", true) && !ctx.packageManager.canRequestPackageInstalls()) {
        // Installing needs the per-app "install unknown apps" switch; send the user there once.
        Toast.makeText(ctx, "Allow SFiles to install apps, then open the file again.", Toast.LENGTH_LONG).show()
        try {
            ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
        return
    }
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
    data class DetailsMulti(val items: List<Entry>) : Dlg
    data class Rename(val e: Entry) : Dlg
    object Delete : Dlg
    object Format : Dlg
    object Unmount : Dlg
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
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val paths = remember(list) { list.map { it.path } }

    fun clear() { selectMode = false; selected = emptySet() }
    fun refresh() { clear(); tick++ }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    fun work(block: () -> String?) = scope.launch {
        val msg = withContext(Dispatchers.IO) { try { block() } catch (e: Exception) { e.message ?: "Failed" } }
        if (msg != null) toast(msg)
        refresh()
    }

    fun op(title: String, done: String, files: List<File>, block: ((Long) -> Unit) -> String?) {
        val started = Ops.run(ctx, OpJob(title, done, files, block) { msg -> if (msg != null) toast(msg); tick++ })
        if (started) clear() else toast("Another operation is still running")
    }

    BackHandler(selectMode) { clear() }
    LaunchedEffect(dir, category) { clear() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Top bar
            if (selectMode) {
                SelectionTopBar(selected.size, selected.isNotEmpty() && selected.size == list.size, {
                    selected = if (selected.size == list.size) emptySet() else list.map { it.path }.toSet()
                }) { clear() }
            } else {
                ScreenHeader(category?.label, onBack) {
                    OverflowMenu { close ->
                        MenuItem("Edit") { close(); selectMode = true }
                        MenuItem(if (Fs.grid) "List view" else "Grid view") { close(); Fs.updateGrid(!Fs.grid) }
                        if (dir != null) MenuItem("Create folder") { close(); dlg = Dlg.NewFolder }
                        if (dir != null && Fs.isRemovable(dir)) {
                            MenuItem("Unmount") { close(); dlg = Dlg.Unmount }
                            MenuItem("Format drive") { close(); dlg = Dlg.Format }
                        }
                        MenuItem("Settings") { close(); onSettings() }
                    }
                }
            }
            if (dir != null) Breadcrumb(dir, onFolder, Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp))
            // Sort / filter row
            if (category == null) SortRow(filter, { filter = it }, sort, { sort = it }, asc, { asc = !asc })


            if (entries == null) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            else if (list.isEmpty()) EmptyState(
                when (category) {
                    Category.Recent -> "No recent files"
                    Category.Favorites -> "No favorites"
                    else -> "No files"
                },
                if (category == Category.Favorites) "Select files or folders, then tap More > Add to favorites." else null,
            )
            else if (Fs.grid) LazyVerticalGrid(
                GridCells.Fixed(4), Modifier.fillMaxSize().padding(horizontal = 12.dp).dragSelectGrid(gridState, paths, selected, { selected = it }, { selectMode = true }),
                state = gridState, contentPadding = PaddingValues(top = 4.dp, bottom = 110.dp),
            ) {
                items(list, key = { it.path }) { e ->
                    GridTile(
                        e, selectMode, e.path in selected,
                        onClick = {
                            if (selectMode) selected = if (e.path in selected) selected - e.path else selected + e.path
                            else if (e.isDir) onFolder(e.file) else openFile(ctx, e.file)
                        },
                        onLongClick = { selectMode = true; selected = selected + e.path },
                    )
                }
            }
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
                        subtitle = if (category != null) e.file.parentFile?.let(::shortPath) else null, modifier = Modifier.animateItem())
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
            ActionBar {
                val has = sel.isNotEmpty()
                BarAction(Icons.Outlined.DriveFileMove, "Move", has) { dlg = Dlg.Pick(true) }
                BarAction(Icons.Outlined.ContentCopy, "Copy", has) { dlg = Dlg.Pick(false) }
                BarAction(Icons.Outlined.Share, "Share", has) { share(ctx, sel.map { it.file }) }
                BarAction(Icons.Outlined.Delete, "Delete", has) { dlg = Dlg.Delete }
                var more by remember { mutableStateOf(false) }
                Row(Modifier.weight(1f)) {
                    BarAction(Icons.Filled.MoreVert, "More", has) { more = true }
                    AppMenu(more, { more = false }) {
                        MenuItem("Details") { more = false; dlg = if (sel.size == 1) Dlg.Details(sel[0]) else Dlg.DetailsMulti(sel.toList()) }
                        if (sel.size == 1) MenuItem("Rename") { more = false; dlg = Dlg.Rename(sel[0]) }
                        MenuItem("Compress") {
                            more = false
                            val files = sel.map { it.file }
                            op("Compressing ${itemsText(files.size)}", "Compressed ${itemsText(files.size)}", files) { cb ->
                                Fs.zip(files, files.first().parentFile!!, cb); null
                            }
                        }
                        val allFav = sel.isNotEmpty() && sel.all { it.path in Fs.favorites }
                        MenuItem(if (allFav) "Remove from favorites" else "Add to favorites") {
                            more = false; Fs.setFavorite(sel.map { it.path }, !allFav); refresh()
                        }
                        val allHidden = sel.isNotEmpty() && sel.all { it.name.startsWith(".") }
                        MenuItem(if (allHidden) "Unhide" else "Hide") {
                            more = false
                            val items = sel.map { it.file }
                            work {
                                val ok = items.all {
                                    val n = if (allHidden) it.name.trimStart('.').ifEmpty { it.name } else if (it.name.startsWith(".")) it.name else "." + it.name
                                    n == it.name || Fs.rename(it, n)
                                }
                                if (!ok) "Couldn't ${if (allHidden) "unhide" else "hide"} some items"
                                else if (!allHidden && !Fs.showHidden) "Hidden. Turn on Show hidden files in Settings to see them." else null
                            }
                        }
                    }
                }
            }
        }
    }

    when (val d = dlg) {
        is Dlg.Details -> DetailsDialog(d.e) { dlg = null }
        is Dlg.DetailsMulti -> MultiDetailsDialog(d.items) { dlg = null }
        is Dlg.Rename -> {
            // With extensions hidden, only the base name is editable, so the extension can't change by accident.
            val keepExt = Fs.hideExt && !d.e.isDir && d.e.file.extension.isNotEmpty()
            var name by remember { mutableStateOf(if (keepExt) d.e.file.nameWithoutExtension else d.e.name) }
            val finalName = if (keepExt) name + "." + d.e.file.extension else name
            AppDialog(
                onDismissRequest = { dlg = null }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
                title = { Text("Rename", fontWeight = FontWeight.Bold) },
                text = {
                    AppTextField(
                        name, { name = it }, autoFocus = true, selectAll = true,
                        suffix = { if (keepExt) Text("." + d.e.file.extension, color = Muted) },
                        supporting = {
                            if (name.startsWith(".")) Text("Names starting with a dot are hidden.")
                            else if (!keepExt && !d.e.isDir && File(name).extension != d.e.file.extension) Text("Changing the extension changes how this file opens.")
                        },
                    )
                },
                confirmButton = { TextButton({ dlg = null; work { if (Fs.rename(d.e.file, finalName)) null else "Couldn't rename" } }, enabled = name.isNotBlank() && finalName != d.e.name) { Text("Rename") } },
                dismissButton = { TextButton({ dlg = null }) { Text("Cancel") } },
            )
        }
        Dlg.NewFolder -> {
            var n by remember { mutableStateOf("") }
            AppDialog(
                onDismissRequest = { dlg = null }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
                title = { Text("Create folder", fontWeight = FontWeight.Bold) },
                text = { AppTextField(n, { n = it }, placeholder = "Folder name", autoFocus = true) },
                confirmButton = { TextButton({ dlg = null; work { if (n.isNotBlank() && File(dir!!, n).mkdirs()) null else "Couldn't create folder" } }, enabled = n.isNotBlank()) { Text("Create") } },
                dismissButton = { TextButton({ dlg = null }) { Text("Cancel") } },
            )
        }
        Dlg.Delete -> AppDialog(
            onDismissRequest = { dlg = null }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
            title = { Text(if (!Fs.useTrash || sel.any { Fs.isRemovable(it.file) }) "Delete permanently?" else "Move to Trash?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (!Fs.useTrash) "${itemsText(sel.size)} will be deleted and can't be recovered."
                    else if (sel.any { Fs.isRemovable(it.file) }) "Items on a USB drive can't be recovered. SFiles doesn't create a trash folder on removable drives."
                    else "${itemsText(sel.size)} will be moved to the trash.",
                )
            },
            confirmButton = {
                TextButton({
                    dlg = null; val files = sel.map { it.file }
                    val perm = !Fs.useTrash || files.any { Fs.isRemovable(it) }
                    op(
                        "${if (perm) "Deleting" else "Moving to Trash:"} ${itemsText(files.size)}",
                        "${if (perm) "Deleted" else "Moved to Trash:"} ${itemsText(files.size)}", files,
                    ) { cb -> if (files.all { cb(0); Fs.trash(it, cb) }) null else "Some items couldn't be deleted" }
                }) { Text(if (!Fs.useTrash || sel.any { Fs.isRemovable(it.file) }) "Delete" else "Move to Trash") }
            },
            dismissButton = { TextButton({ dlg = null }) { Text("Cancel") } },
        )
        Dlg.Format -> DriveSettingsDialog(false) { dlg = null }
        Dlg.Unmount -> DriveSettingsDialog(true) { dlg = null }
        is Dlg.Pick -> FolderPicker(
            start = dir ?: Fs.root, actionLabel = if (d.move) "Move here" else "Copy here",
            onDismiss = { dlg = null },
            onPicked = { target ->
                dlg = null; val files = sel.map { it.file }
                val verb = if (d.move) "Moving" else "Copying"
                op("$verb ${itemsText(files.size)}", "${if (d.move) "Moved" else "Copied"} ${itemsText(files.size)}", files) { cb ->
                    val ok = files.all { if (d.move) Fs.move(it, target, cb) else Fs.copy(it, target, cb) }
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
            AppMenu(fm, { fm = false }) {
                Kind.values().filter { it != Kind.Other }.forEach { k -> MenuItem(k.label) { fm = false; onFilter(k) } }
            }
        }
        Spacer(Modifier.weight(1f))
        Box {
            Row(Modifier.clickable { sm = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.Sort, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(sort.label, fontSize = 15.sp)
            }
            AppMenu(sm, { sm = false }) {
                SortBy.values().forEach { s -> MenuItem(s.label) { sm = false; onSort(s) } }
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(1.dp).height(18.dp).background(Divider))
        Spacer(Modifier.width(10.dp))
        Icon(if (asc) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward, null, modifier = Modifier.size(22.dp).clickable { onAsc() })
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
    AppDialog(
        onDismissRequest = onDismiss, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
        title = { Text("Details", fontWeight = FontWeight.Bold) },
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
        confirmButton = { TextButton(onDismiss) { Text("OK") } },
    )
}

/** Details for several selected items: how many, their total size (folders included) and the date ranges. */
@Composable
private fun MultiDetailsDialog(items: List<Entry>, onDismiss: () -> Unit) {
    class Info(val size: Long, val files: Int, val folders: Int, val created: List<Long>)
    val info by produceState<Info?>(null, items) {
        value = withContext(Dispatchers.IO) {
            var size = 0L; var files = 0; var folders = 0
            val created = ArrayList<Long>()
            for (e in items) {
                size += Fs.treeSize(e.file)
                if (e.isDir) { val (f, d) = Fs.contents(e.file); files += f; folders += d + 1 } else files++
                Fs.createdAt(e.file).takeIf { it > 0 }?.let(created::add)
            }
            Info(size, files, folders, created)
        }
    }
    fun range(v: List<Long>) = if (v.isEmpty()) "\u2026" else if (v.min() == v.max()) formatDate(v.min()) else formatDate(v.min()) + " - " + formatDate(v.max())
    AppDialog(
        onDismissRequest = onDismiss, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
        title = { Text("Details", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val first = items.first()
                    if (first.isDir) FolderIcon(first.file, Modifier.scale(0.9f)) else Thumb(first.file)
                    Spacer(Modifier.width(16.dp)); Text("${items.size} items", fontSize = 17.sp)
                }
                Field("Total size (all folders and files)", info?.size?.let(::formatSize) ?: "Calculating\u2026")
                Field("Last modified", range(items.map { it.modified }))
                Field("Added to device", info?.let { range(it.created) } ?: "\u2026")
                info?.let { i ->
                    Field("Contains", buildString {
                        append("${i.files} file${if (i.files == 1) "" else "s"}")
                        if (i.folders > 0) append(", ${i.folders} folder${if (i.folders == 1) "" else "s"}")
                    })
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("OK") } },
    )
}

@Composable
private fun Field(label: String, value: String) = Column {
    Text(label, fontSize = 14.sp, color = Muted)
    Text(value, fontSize = 17.sp)
}

private fun Modifier.scale(s: Float) = this.then(Modifier.graphicsLayer(scaleX = s, scaleY = s))
