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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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

/** List of saved connections, or the empty state with the add button. */
@Composable
fun NetworkScreen(onBack: () -> Unit, onOpen: (Conn) -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val conns = remember(tick) { ConnStore.all(ctx) }
    var addDialog by remember { mutableStateOf(false) }
    var smbScan by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf<FormReq?>(null) }
    var menuFor by remember { mutableStateOf<Conn?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Network storage", onBack) {
            if (conns.isNotEmpty()) TextButton({ addDialog = true }) { Text("Add", fontSize = 16.sp) }
        }
        HomeCrumb("Network storage", onBack)
        if (conns.isEmpty()) {
            EmptyState(
                "No network storage", "Access servers and network drives from your phone by adding them to SFiles.",
                Modifier.padding(bottom = 80.dp),
            ) {
                Text(
                    "Add network storage", fontSize = 18.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(CircleShape).background(Pill).clickable { addDialog = true }.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        } else LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(conns, key = { it.id }) { c ->
                @OptIn(ExperimentalFoundationApi::class)
                Row(
                    Modifier.fillMaxWidth().combinedClickable(onClick = { onOpen(c) }, onLongClick = { menuFor = c }).padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Dns, null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(c.name, fontSize = 17.sp)
                        Text(c.url, fontSize = 13.sp, color = Muted)
                    }
                }
            }
        }
    }
    if (addDialog) AppSheet({ addDialog = false }) {
        Text("Add network storage", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
        Proto.values().forEach { p ->
            Text(
                p.label, fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth().clickable { addDialog = false; if (p == Proto.SMB) smbScan = true else form = FormReq(p, null, null) }.padding(vertical = 18.dp),
            )
        }
        DialogButtonRow(left = null, right = { TextButton({ addDialog = false }) { Text("Cancel") } })
    }
    if (smbScan) SmbDiscoveryDialog({ smbScan = false }, { smbScan = false; form = FormReq(Proto.SMB, null, null) }) { smbScan = false; form = FormReq(Proto.SMB, null, it) }
    form?.let { r -> ConnFormDialog(r.proto, r.existing, r.host, { form = null }) { form = null; tick++ } }
    menuFor?.let { c ->
        AppDialog(
            onDismissRequest = { menuFor = null }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
            title = { Text(c.name, fontWeight = FontWeight.Bold) },
            confirmButton = { TextButton({ menuFor = null; form = FormReq(c.proto, c, null) }) { Text("Edit") } },
            dismissButton = { TextButton({ ConnStore.remove(ctx, c.id); menuFor = null; tick++ }) { Text("Remove") } },
        )
    }
}

/** "Select a network drive": scans the Wi-Fi network for SMB servers, or add one by hand. */
@Composable
private fun SmbDiscoveryDialog(onDismiss: () -> Unit, onManual: () -> Unit, onPick: (String) -> Unit) {
    val found by produceState<List<Found>?>(null) { value = withContext(Dispatchers.IO) { runCatching { discoverSmb() }.getOrDefault(emptyList()) } }
    AppSheet(onDismiss) {
        Text("Select a network drive", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
        val list = found
        if (list == null) Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(20.dp))
            Text("Looking for drives on current network\u2026", fontSize = 15.sp, color = Muted)
        } else if (list.isEmpty()) Text("No drives found on this network.", fontSize = 15.sp, color = Muted, modifier = Modifier.padding(vertical = 40.dp))
        else list.forEach { f ->
            Row(Modifier.fillMaxWidth().clickable { onPick(f.host) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Tint), contentAlignment = Alignment.Center) {
                    Text("SMB", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Accent)
                }
                Spacer(Modifier.width(16.dp))
                Column { Text(f.name, fontSize = 17.sp); Text("${f.host}:445", fontSize = 13.sp, color = Muted) }
            }
        }
        DialogButtonRow(left = { TextButton(onDismiss) { Text("Cancel") } }, right = { TextButton(onManual) { Text("Add manually") } })
    }
}

private data class FormReq(val proto: Proto, val existing: Conn?, val host: String?)

/** Add / edit sheet. Tests the connection before saving. */
@Composable
private fun ConnFormDialog(proto: Proto, existing: Conn?, prefillHost: String?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var host by remember { mutableStateOf(existing?.host ?: prefillHost ?: "") }
    var port by remember { mutableStateOf((existing?.port ?: proto.defaultPort).toString()) }
    var user by remember { mutableStateOf(existing?.user ?: "") }
    var pass by remember { mutableStateOf(existing?.pass ?: "") }
    var anon by remember { mutableStateOf(existing?.anonymous ?: false) }
    var path by remember { mutableStateOf(existing?.path ?: "") }
    var tls by remember { mutableStateOf(existing?.ftpTls ?: false) }
    var implicit by remember { mutableStateOf(existing?.ftpImplicit ?: false) }
    var active by remember { mutableStateOf(existing?.ftpActive ?: false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val canAnon = proto != Proto.SFTP
    val ready = host.isNotBlank() && (proto != Proto.SMB || path.isNotBlank())

    fun save() {
        val c = Conn(
            existing?.id ?: java.util.UUID.randomUUID().toString(), proto,
            name.ifBlank { host.trim() }, host.trim(), port.toIntOrNull() ?: proto.defaultPort,
            user.trim(), pass, anon && canAnon, path.trim(),
            proto == Proto.FTP && tls, proto == Proto.FTP && tls && implicit, proto == Proto.FTP && active,
        )
        busy = true; error = null
        scope.launch {
            val err = withContext(Dispatchers.IO) {
                try { openRemote(ctx, c).use { it.list(startPath(it, c)) }; null } catch (e: Exception) { e.message ?: e.javaClass.simpleName }
            }
            busy = false
            if (err == null) { ConnStore.put(ctx, c); onSaved() } else error = "Couldn't connect: $err"
        }
    }

    AppSheet(onDismiss) {
        Text(proto.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            UField("Address", host, KeyboardType.Uri) { host = it }
            UField("Port", port, KeyboardType.Number) { port = it.filter(Char::isDigit) }
            if (proto == Proto.SMB) UField("Share name", path) { path = it }
            if (proto == Proto.FTP) Choice("Security", if (tls) "SSL/TLS" else "None", listOf("None", "SSL/TLS")) {
                tls = it == 1
                if (!tls && port == "990") port = "21"
            }
            UField("Username", user, enabled = !anon) { user = it }
            UField("Password", pass, KeyboardType.Password, secret = true, enabled = !anon) { pass = it }
            if (canAnon) Row(Modifier.fillMaxWidth().offset(x = (-12).dp).clickable { anon = !anon }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(anon, { anon = it })
                Text("Sign in anonymously", fontSize = 16.sp)
            }
            if (proto == Proto.FTP) {
                if (tls) Choice("Encryption", if (implicit) "Implicit" else "Explicit", listOf("Explicit", "Implicit")) {
                    implicit = it == 1
                    if (implicit && port == "21") port = "990" else if (!implicit && port == "990") port = "21"
                }
                Choice("Transfer mode", if (active) "Active" else "Passive", listOf("Passive", "Active")) { active = it == 1 }
            }
            if (proto != Proto.SMB) UField("Start folder (optional)", path) { path = it }
            UField("Display name", name) { name = it }
            error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp) }
        }
        Spacer(Modifier.height(16.dp))
        DialogButtonRow(
            left = { TextButton(onDismiss, enabled = !busy) { Text("Cancel") } },
            right = {
                TextButton(::save, enabled = !busy && ready) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(if (existing != null) "Save" else "Add")
                }
            },
        )
    }
}

@Composable
private fun UField(
    label: String, value: String, type: KeyboardType = KeyboardType.Text, secret: Boolean = false, enabled: Boolean = true, onChange: (String) -> Unit,
) = AppTextField(value, onChange, label = label, enabled = enabled, keyboardType = type, secret = secret)

/** Underlined dropdown that matches [UField]. */
@Composable
private fun Choice(label: String, value: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.fillMaxWidth().clickable { open = true }.padding(top = 8.dp)) {
            Text(label, fontSize = 12.sp, color = Accent)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(value, Modifier.weight(1f), fontSize = 17.sp)
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            HorizontalDivider(color = Divider)
        }
        AppMenu(open, { open = false }) {
            options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onPick(i) }) }
        }
    }
}

private sealed interface RDlg {
    data class Rename(val i: RItem) : RDlg
    object NewFolder : RDlg
    object Delete : RDlg
}

@Composable
fun RemoteBrowserScreen(conn: Conn, onSettings: () -> Unit, onBack: () -> Unit) {
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
            // A cached connection can die (idle timeout, Wi-Fi change). Drop it on any error so the next
            // call reconnects, and retry once right away when the failed connection was an old one.
            fun drop() { val f = holder[0]; holder[0] = null; if (f != null) runCatching { f.close() } }
            val cached = holder[0]
            try {
                block(cached ?: openRemote(ctx, conn).also { holder[0] = it })
            } catch (e: Exception) {
                drop()
                if (cached == null) throw e
                try { block(openRemote(ctx, conn).also { holder[0] = it }) } catch (e2: Exception) { drop(); throw e2 }
            }
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (selectMode) {
                SelectionTopBar(selected.size, selected.isNotEmpty() && selected.size == items.size, {
                    selected = if (selected.size == items.size) emptySet() else items.map { it.path }.toSet()
                }) { clear() }
            } else {
                ScreenHeader(null, onBack) {
                    OverflowMenu { close ->
                        MenuItem("Sync") { close(); tick++ }
                        MenuItem("Select") { close(); selectMode = true }
                        MenuItem("Create folder") { close(); dlg = RDlg.NewFolder }
                        MenuItem("Settings") { close(); onSettings() }
                    }
                }
            }
            // breadcrumb: home > connection name > folders
            val base = root ?: "/"
            val segs = (cur ?: base).removePrefix(base).split('/').filter { it.isNotEmpty() }
            val targets = segs.runningFold(base) { acc, seg -> joinPath(acc, seg) } // targets[0] = base
            Crumbs(listOf(conn.name) + segs, onBack, { i -> path = targets[i] }, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            when {
                listing == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                listing!!.isFailure -> EmptyState("Couldn't load this folder", listing!!.exceptionOrNull()?.message ?: "") {
                    TextButton({ holder[0]?.let { f -> Thread { runCatching { f.close() } }.start() }; holder[0] = null; tick++ }) { Text("Retry") }
                }
                items.isEmpty() -> EmptyState("No files")
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
            ActionBar {
                val has = sel.isNotEmpty()
                BarAction(Icons.Outlined.Download, "Download", has) {
                    val s = sel
                    run("Saved to Download") { fs ->
                        val dest = File(Fs.root, "Download").apply { mkdirs() }
                        s.forEach { fs.downloadTree(it, dest) }
                    }
                }
                BarAction(Icons.Outlined.DriveFileRenameOutline, "Rename", sel.size == 1) { dlg = RDlg.Rename(sel[0]) }
                BarAction(Icons.Outlined.Delete, "Delete", has) { dlg = RDlg.Delete }
            }
        }
    }

    when (val d = dlg) {
        is RDlg.Rename -> {
            var n by remember { mutableStateOf(d.i.name) }
            AppDialog(
                onDismissRequest = { dlg = null }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
                title = { Text("Rename", fontWeight = FontWeight.Bold) },
                text = { AppTextField(n, { n = it }, autoFocus = true, selectAll = true) },
                confirmButton = { TextButton({ dlg = null; run("Renamed") { fs -> fs.rename(d.i.path, joinPath(parentPath(d.i.path), n)) } }, enabled = n.isNotBlank() && n != d.i.name) { Text("Rename") } },
                dismissButton = { TextButton({ dlg = null }) { Text("Cancel") } },
            )
        }
        RDlg.NewFolder -> {
            var n by remember { mutableStateOf("") }
            AppDialog(
                onDismissRequest = { dlg = null }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
                title = { Text("Create folder", fontWeight = FontWeight.Bold) },
                text = { AppTextField(n, { n = it }, placeholder = "Folder name", autoFocus = true) },
                confirmButton = { TextButton({ dlg = null; if (n.isNotBlank() && cur != null) run("Folder created") { fs -> fs.mkdir(joinPath(cur, n)) } }, enabled = n.isNotBlank()) { Text("Create") } },
                dismissButton = { TextButton({ dlg = null }) { Text("Cancel") } },
            )
        }
        RDlg.Delete -> AppDialog(
            onDismissRequest = { dlg = null }, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
            title = { Text("Delete permanently?", fontWeight = FontWeight.Bold) },
            text = { Text("${itemsText(sel.size)} will be deleted from the server. This can't be undone.") },
            confirmButton = { TextButton({ dlg = null; val s = sel; run("Deleted") { fs -> s.forEach { fs.delete(it) } } }) { Text("Delete") } },
            dismissButton = { TextButton({ dlg = null }) { Text("Cancel") } },
        )
        null -> {}
    }
}
