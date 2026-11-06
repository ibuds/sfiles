package io.github.ibuds.sfiles

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

@Composable
private fun Top(title: String?, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        if (title != null) Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** List of saved connections, or the empty state with the add button. */
@Composable
fun NetworkScreen(onBack: () -> Unit, onOpen: (Conn) -> Unit, onAdd: (Proto) -> Unit, onEdit: (Conn) -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val conns = remember(tick) { ConnStore.all(ctx) }
    var addDialog by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Conn?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Top(null, onBack) {
            if (conns.isNotEmpty()) TextButton({ addDialog = true }) { Text("Add", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp) }
        }
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(CircleShape).background(Pill).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Home, null, tint = Muted, modifier = Modifier.size(22.dp).clickable { onBack() })
            Icon(Icons.Outlined.ChevronRight, null, tint = Muted, modifier = Modifier.padding(horizontal = 6.dp).size(18.dp))
            Text("Network storage", fontSize = 15.sp, color = Accent, fontWeight = FontWeight.Bold)
        }
        if (conns.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("Access servers and network drives from your phone by adding them to SFiles.", fontSize = 17.sp, color = Muted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Text(
                    "Add network storage", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(CircleShape).background(Pill).clickable { addDialog = true }.padding(horizontal = 24.dp, vertical = 16.dp),
                )
                Spacer(Modifier.height(80.dp))
            }
        } else LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(conns, key = { it.id }) { c ->
                @OptIn(ExperimentalFoundationApi::class)
                Row(
                    Modifier.fillMaxWidth().combinedClickable(onClick = { onOpen(c) }, onLongClick = { menuFor = c }).padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Dns, null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(c.name, fontSize = 17.sp)
                        Text(c.url, fontSize = 13.sp, color = Muted)
                    }
                }
            }
        }
    }
    if (addDialog) androidx.compose.ui.window.Dialog({ addDialog = false }) {
        Column(Modifier.clip(RoundedCornerShape(32.dp)).background(Sheet).padding(vertical = 24.dp)) {
            Text("Add network storage", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Proto.values().forEach { p ->
                Text(
                    p.label, fontSize = 18.sp,
                    modifier = Modifier.fillMaxWidth().clickable { addDialog = false; onAdd(p) }.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
            Text("Cancel", fontSize = 18.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { addDialog = false }.padding(top = 16.dp))
        }
    }
    menuFor?.let { c ->
        AlertDialog(
            onDismissRequest = { menuFor = null }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
            title = { Text(c.name, fontWeight = FontWeight.Bold) },
            confirmButton = { TextButton({ menuFor = null; onEdit(c) }) { Text("Edit") } },
            dismissButton = { TextButton({ ConnStore.remove(ctx, c.id); menuFor = null; tick++ }) { Text("Remove") } },
        )
    }
}

/** Add / edit form. Tests the connection before saving. */
@Composable
fun ConnFormScreen(proto: Proto, existing: Conn?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var port by remember { mutableStateOf((existing?.port ?: proto.defaultPort).toString()) }
    var user by remember { mutableStateOf(existing?.user ?: "") }
    var pass by remember { mutableStateOf(existing?.pass ?: "") }
    var anon by remember { mutableStateOf(existing?.anonymous ?: false) }
    var path by remember { mutableStateOf(existing?.path ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val canAnon = proto != Proto.SFTP

    fun save() {
        val c = Conn(
            existing?.id ?: java.util.UUID.randomUUID().toString(), proto,
            name.ifBlank { host }, host.trim(), port.toIntOrNull() ?: proto.defaultPort,
            user.trim(), pass, anon && canAnon, path.trim(),
        )
        if (c.host.isEmpty()) { error = "Enter a server address"; return }
        if (proto == Proto.SMB && c.path.isEmpty()) { error = "Enter the share name"; return }
        busy = true; error = null
        scope.launch {
            val err = withContext(Dispatchers.IO) {
                try { openRemote(ctx, c).use { it.list(startPath(it, c)) }; null } catch (e: Exception) { e.message ?: e.javaClass.simpleName }
            }
            busy = false
            if (err == null) { ConnStore.put(ctx, c); onDone() } else error = "Couldn't connect: $err"
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
        Top(proto.label, onDone)
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("Name (optional)", name) { name = it }
            Field("Server address", host, KeyboardType.Uri) { host = it }
            Field("Port", port, KeyboardType.Number) { port = it.filter(Char::isDigit) }
            if (proto == Proto.SMB) Field("Share name", path) { path = it }
            else Field("Start folder (optional)", path) { path = it }
            if (canAnon) Row(Modifier.fillMaxWidth().clickable { anon = !anon }, verticalAlignment = Alignment.CenterVertically) {
                Text(if (proto == Proto.SMB) "Guest login" else "Anonymous", Modifier.weight(1f), fontSize = 16.sp)
                Switch(anon, { anon = it })
            }
            if (!anon || !canAnon) {
                Field("Username", user) { user = it }
                Field("Password", pass, KeyboardType.Password, hide = true) { pass = it }
            }
            error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp) }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onDone, enabled = !busy) { Text("Cancel", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
                TextButton(::save, enabled = !busy) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Save", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType = KeyboardType.Text, hide: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = type),
        visualTransformation = if (hide) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    )
}

private sealed interface RDlg {
    data class Rename(val i: RItem) : RDlg
    object NewFolder : RDlg
    object Delete : RDlg
}

@Composable
fun RemoteBrowserScreen(conn: Conn, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lock = remember { Mutex() }
    val holder = remember { arrayOfNulls<RemoteFs>(1) }
    var root by remember { mutableStateOf<String?>(null) }
    var path by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    var dlg by remember { mutableStateOf<RDlg?>(null) }
    var menu by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { val f = holder[0]; if (f != null) Thread { runCatching { f.close() } }.start() } }

    suspend fun <T> withFs(block: (RemoteFs) -> T): T = lock.withLock {
        withContext(Dispatchers.IO) {
            val fs = holder[0] ?: openRemote(ctx, conn).also { holder[0] = it }
            block(fs)
        }
    }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

    val listing by produceState<Result<List<RItem>>?>(null, path, tick) {
        value = try {
            Result.success(withFs { fs ->
                if (path == null) { val s = startPath(fs, conn); root = s; path = s }
                fs.list(path ?: startPath(fs, conn))
            }.sortedWith(compareByDescending<RItem> { it.isDir }.thenBy { it.name.lowercase() }))
        } catch (e: Exception) { Result.failure(e) }
    }
    val items = listing?.getOrNull() ?: emptyList()
    val sel = items.filter { it.path in selected }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val paths = remember(items) { items.map { it.path } }

    fun clear() { selectMode = false; selected = emptySet() }
    fun run(label: String, block: (RemoteFs) -> Unit) = scope.launch {
        busy = true
        val err = try { withFs(block); null } catch (e: Exception) { e.message ?: "Failed" }
        busy = false; clear(); tick++
        (err ?: label).let { if (it.isNotEmpty()) toast(it) }
    }

    BackHandler(selectMode) { clear() }
    val cur = path
    BackHandler(!selectMode && cur != null && root != null && cur != root) { path = parentPath(cur!!) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty() || cur == null) return@rememberLauncherForActivityResult
        run("Uploaded ${uris.size} file(s)") { fs ->
            for (u in uris) {
                val name = ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: "upload"
                val tmp = File(ctx.cacheDir, "up_$name")
                ctx.contentResolver.openInputStream(u)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                try { fs.upload(tmp, joinPath(cur, name)) } finally { tmp.delete() }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (selectMode) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.clip(CircleShape).background(Pill).clickable {
                            selected = if (selected.size == items.size) emptySet() else items.map { it.path }.toSet()
                        }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Check(selected.isNotEmpty() && selected.size == items.size)
                        Spacer(Modifier.width(12.dp)); Text("${selected.size}", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Cancel", fontSize = 18.sp, modifier = Modifier.clip(CircleShape).background(Pill).clickable { clear() }.padding(horizontal = 24.dp, vertical = 12.dp))
                }
            } else {
                Top(null, onBack) {
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                        DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(24.dp), containerColor = MenuBg) {
                            DropdownMenuItem({ Text("Upload files", fontSize = 17.sp) }, { menu = false; picker.launch(arrayOf("*/*")) })
                            DropdownMenuItem({ Text("New folder", fontSize = 17.sp) }, { menu = false; dlg = RDlg.NewFolder })
                            DropdownMenuItem({ Text("Refresh", fontSize = 17.sp) }, { menu = false; tick++ })
                        }
                    }
                }
            }
            // breadcrumb: home > connection name > folders
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(CircleShape).background(Pill).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Home, null, tint = Muted, modifier = Modifier.size(22.dp).clickable { onBack() })
                val base = root ?: "/"
                val segs = (cur ?: base).removePrefix(base).split('/').filter { it.isNotEmpty() }
                Icon(Icons.Outlined.ChevronRight, null, tint = Muted, modifier = Modifier.padding(horizontal = 6.dp).size(18.dp))
                Text(
                    conn.name, fontSize = 15.sp, maxLines = 1,
                    color = if (segs.isEmpty()) Accent else Muted, fontWeight = if (segs.isEmpty()) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clickable { path = base },
                )
                var acc = base
                segs.forEachIndexed { i, s ->
                    acc = joinPath(acc, s); val target = acc
                    Icon(Icons.Outlined.ChevronRight, null, tint = Muted, modifier = Modifier.padding(horizontal = 6.dp).size(18.dp))
                    val last = i == segs.lastIndex
                    Text(s, fontSize = 15.sp, maxLines = 1, color = if (last) Accent else Muted,
                        fontWeight = if (last) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.clickable { path = target })
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                listing == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                listing!!.isFailure -> Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                    Text("Couldn't load this folder", fontSize = 18.sp)
                    Text(listing!!.exceptionOrNull()?.message ?: "", color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    TextButton({ holder[0]?.let { f -> Thread { runCatching { f.close() } }.start() }; holder[0] = null; tick++ }) { Text("Retry", color = MaterialTheme.colorScheme.onSurface) }
                }
                items.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No files", color = Muted) }
                else -> LazyColumn(
                    Modifier.fillMaxSize().dragSelect(listState, paths, selected, { selected = it }, { selectMode = true }),
                    state = listState, contentPadding = PaddingValues(bottom = 110.dp),
                ) {
                    items(items, key = { it.path }) { r ->
                        val e = Entry(File(r.name), r.isDir, r.modified, r.size, 0)
                        EntryRow(
                            e, selectMode, r.path in selected, remote = true, countText = if (r.isDir) "" else null,
                            onClick = {
                                if (selectMode) selected = if (r.path in selected) selected - r.path else selected + r.path
                                else if (r.isDir) path = r.path
                                else run("") { fs ->
                                    val dir = File(ctx.cacheDir, "remote").apply { mkdirs() }
                                    val f = File(dir, r.name)
                                    fs.download(r.path, f)
                                    scope.launch { openFile(ctx, f) }
                                }
                            },
                            onLongClick = { selectMode = true; selected = selected + r.path }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
        if (busy) Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(enabled = true) {}, Alignment.Center) { CircularProgressIndicator() }
        AnimatedVisibility(
            selectMode, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(260)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
        ) {
            Row(
                Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
                    .fillMaxWidth().clip(CircleShape).background(Bar).padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val has = sel.isNotEmpty()
                RAction(Icons.Outlined.Download, "Download", has) {
                    val s = sel
                    run("Saved to Download") { fs ->
                        val dest = File(Fs.root, "Download").apply { mkdirs() }
                        s.forEach { fs.downloadTree(it, dest) }
                    }
                }
                RAction(Icons.Outlined.DriveFileRenameOutline, "Rename", sel.size == 1) { dlg = RDlg.Rename(sel[0]) }
                RAction(Icons.Outlined.Delete, "Delete", has) { dlg = RDlg.Delete }
            }
        }
    }

    when (val d = dlg) {
        is RDlg.Rename -> {
            var n by remember { mutableStateOf(d.i.name) }
            AlertDialog(
                onDismissRequest = { dlg = null }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
                title = { Text("Rename", fontWeight = FontWeight.Bold) },
                text = { OutlinedTextField(n, { n = it }, singleLine = true) },
                confirmButton = { TextButton({ dlg = null; run("Renamed") { fs -> fs.rename(d.i.path, joinPath(parentPath(d.i.path), n)) } }) { Text("Rename") } },
                dismissButton = { TextButton({ dlg = null }) { Text("Cancel") } },
            )
        }
        RDlg.NewFolder -> {
            var n by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { dlg = null }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
                title = { Text("Create folder", fontWeight = FontWeight.Bold) },
                text = { OutlinedTextField(n, { n = it }, singleLine = true, placeholder = { Text("Folder name") }) },
                confirmButton = { TextButton({ dlg = null; if (n.isNotBlank() && cur != null) run("Folder created") { fs -> fs.mkdir(joinPath(cur, n)) } }) { Text("Create") } },
                dismissButton = { TextButton({ dlg = null }) { Text("Cancel") } },
            )
        }
        RDlg.Delete -> AlertDialog(
            onDismissRequest = { dlg = null }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
            title = { Text("Delete permanently?", fontWeight = FontWeight.Bold) },
            text = { Text("${sel.size} item(s) will be deleted from the server. This can't be undone.") },
            confirmButton = { TextButton({ dlg = null; val s = sel; run("Deleted") { fs -> s.forEach { fs.delete(it) } } }) { Text("Delete") } },
            dismissButton = { TextButton({ dlg = null }) { Text("Cancel") } },
        )
        null -> {}
    }
}

@Composable
private fun RAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val c = if (enabled) MaterialTheme.colorScheme.onSurface else Color(0xFF666666)
        Icon(icon, null, tint = c, modifier = Modifier.size(24.dp)); Text(label, fontSize = 12.sp, color = c)
    }
}
