package io.github.ibuds.sfiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class Cat(val c: Category, val icon: ImageVector?, val bg: Color, val fg: Color)

private val cats = listOf(
    Cat(Category.Downloads, Icons.Outlined.Download, Color(0xFF1E3B37), Color(0xFF4DD0B0)),
    Cat(Category.Docs, Icons.Outlined.Description, Color(0xFF4A3A22), Color(0xFFF0B860)),
    Cat(Category.Images, Icons.Outlined.Image, Color(0xFF4A2230), Color(0xFFE8657F)),
    Cat(Category.Audio, Icons.Outlined.MusicNote, Color(0xFF262A4F), Color(0xFF8A93F0)),
    Cat(Category.Videos, Icons.Outlined.PlayArrow, Color(0xFF3A2160), Color(0xFFB07CF5)),
    Cat(Category.Apk, null, Color(0xFF34431A), Color(0xFFA4D93A)),
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(volTick: Int, onVoice: () -> Unit, onRecent: () -> Unit, onFavorites: () -> Unit, onTrash: () -> Unit, onNetwork: () -> Unit, onSettings: () -> Unit, onCategory: (Category) -> Unit, onFolder: (File) -> Unit, onOpenFile: (File) -> Unit, onSearch: () -> Unit) {
    val ctx = LocalContext.current
    val recentOrNull by produceState<List<Entry>?>(null) { value = withContext(Dispatchers.IO) { Fs.recent(ctx) } }
    val recent = recentOrNull ?: emptyList()
    val favs by produceState<List<Entry>>(emptyList(), Fs.favorites, Fs.showHidden) { value = withContext(Dispatchers.IO) { Fs.favoriteEntries() } }
    val vols = remember(volTick) { Fs.vols }
    var driveMenu by remember { mutableStateOf<String?>(null) }
    var driveDialog by remember { mutableStateOf<Boolean?>(null) } // true = unmount, false = format
    val connCount by produceState(0, volTick) { value = withContext(Dispatchers.IO) { ConnStore.all(ctx).size } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("SFiles", fontSize = if (Fs.minimal) 28.sp else 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OverflowMenu { close -> MenuItem("Settings") { close(); onSettings() } }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
            // Categories
            Card {
                Column(Modifier.padding(vertical = 8.dp, horizontal = 12.dp)) {
                    cats.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { c ->
                                Row(
                                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onCategory(c.c) }.padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val fg = if (Fs.minimal) Accent else c.fg
                                    Box(Modifier.size(48.dp).clip(if (Fs.minimal) RoundedCornerShape(14.dp) else CircleShape).background(if (Fs.minimal) Tint else c.fg.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                        if (c.icon != null) Icon(c.icon, null, tint = fg, modifier = Modifier.size(24.dp))
                                        else Text("APK", color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Text(c.c.label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Empty cards stay hidden.
            if (recent.isNotEmpty()) { ThumbCard("Recent files", onRecent, recent, onOpenFile); Spacer(Modifier.height(12.dp)) }
            if (favs.isNotEmpty()) {
                ThumbCard("Favorites", onFavorites, favs) { f -> if (f.isDirectory) onFolder(f) else onOpenFile(f) }
                Spacer(Modifier.height(12.dp))
            }
            SectionLabel("Storage")
            Card {
                Column {
                    vols.forEach { v ->
                        val (used, total) = remember(v) { Fs.storage(v.dir) }
                        Box {
                        Row(
                            Modifier.fillMaxWidth()
                                .combinedClickable(onClick = { onFolder(v.dir) }, onLongClick = { if (v.removable) driveMenu = v.dir.absolutePath })
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (v.removable) Icons.Outlined.Usb else Icons.Outlined.PhoneAndroid, null, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.label, fontSize = 16.sp)
                                Text(listOfNotNull(v.fs, "${(used * 100 / total.coerceAtLeast(1))}% used").joinToString(" · "), fontSize = 13.sp, color = Accent)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${formatSize(used)} / ${formatSize(total)}", fontSize = 12.sp)
                                Spacer(Modifier.height(6.dp))
                                AppProgressBar(used.toFloat() / total.coerceAtLeast(1), Modifier.width(110.dp).height(8.dp), if (Fs.minimal) Accent else Color(0xFF3FB59B))
                            }
                        }
                        // Anchored to the right edge of the row so the menu opens on the right.
                        Box(Modifier.align(Alignment.TopEnd)) {
                            AppMenu(driveMenu == v.dir.absolutePath, { driveMenu = null }) {
                                MenuItem("Unmount") { driveMenu = null; driveDialog = true }
                                MenuItem("Format drive") { driveMenu = null; driveDialog = false }
                            }
                        }
                        }
                    }
                    StorageRow(Icons.Outlined.Dns, "Network storage", connCount.let { if (it == 0) null else "$it connection${if (it == 1) "" else "s"}" }, onNetwork)
                }
            }
            SectionLabel("Utilities")
            Card {
                Column {
                    StorageRow(Icons.Outlined.DeleteOutline, "Trash", null, onTrash)
                }
            }
            Spacer(Modifier.height(100.dp))
            }
        }
        driveDialog?.let { DriveSettingsDialog(it) { driveDialog = null } }
        SearchPill(onSearch, onVoice, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp))
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(if (Fs.minimal) 20.dp else 26.dp)).background(io.github.ibuds.sfiles.Card)) { content() }
}

@Composable
private fun SectionLabel(t: String) = SectionTitle(t, Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp))

@Composable
private fun StorageRow(icon: ImageVector, title: String, sub: String?, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontSize = 16.sp)
            if (sub != null) Text(sub, fontSize = 13.sp, color = Muted)
        }
    }
}

/** Titled card with a horizontal row of thumbnails; the whole title row opens the full list. */
@Composable
private fun ThumbCard(title: String, onTitle: () -> Unit, items: List<Entry>, onItem: (File) -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).clip(RoundedCornerShape(12.dp)).clickable { onTitle() }, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, "See all")
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { e ->
                    Column(Modifier.width(82.dp).clickable { onItem(e.file) }) {
                        if (e.isDir) Box(Modifier.size(82.dp).clip(RoundedCornerShape(8.dp)).background(Tile), contentAlignment = Alignment.Center) { FolderIcon() }
                        else Thumb(e.file, Modifier.size(82.dp))
                        Text(e.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}
