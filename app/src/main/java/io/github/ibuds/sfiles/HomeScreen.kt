package io.github.ibuds.sfiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

@Composable
fun HomeScreen(volTick: Int, onVoice: () -> Unit, onRecent: () -> Unit, onTrash: () -> Unit, onNetwork: () -> Unit, onSettings: () -> Unit, onCategory: (Category) -> Unit, onFolder: (File) -> Unit, onOpenFile: (File) -> Unit, onSearch: () -> Unit) {
    val ctx = LocalContext.current
    val recent by produceState(emptyList<Entry>()) { value = withContext(Dispatchers.IO) { Fs.recent(ctx) } }
    val vols = remember(volTick) { Fs.vols }
    val connCount by produceState(0, volTick) { value = withContext(Dispatchers.IO) { ConnStore.all(ctx).size } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("SFiles", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box {
                    var menu by remember { mutableStateOf(false) }
                    Icon(Icons.Filled.MoreVert, null, modifier = Modifier.padding(end = 4.dp).clickable { menu = true })
                    androidx.compose.material3.DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(24.dp), containerColor = MenuBg) {
                        androidx.compose.material3.DropdownMenuItem({ Text("Settings", fontSize = 17.sp) }, { menu = false; onSettings() })
                    }
                }
            }
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
                                    Box(Modifier.size(48.dp).clip(CircleShape).background(c.fg.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                        if (c.icon != null) Icon(c.icon, null, tint = c.fg, modifier = Modifier.size(24.dp))
                                        else Text("APK", color = c.fg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
            // Recent files
            Card {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent files", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.clickable { onRecent() })
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recent.forEach { e ->
                            Column(Modifier.width(82.dp).clickable { onOpenFile(e.file) }) {
                                Thumb(e.file, Modifier.size(82.dp))
                                Text(e.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
            SectionLabel("Storage")
            Card {
                Column {
                    vols.forEach { v ->
                        val (used, total) = remember(v) { Fs.storage(v.dir) }
                        Row(Modifier.fillMaxWidth().clickable { onFolder(v.dir) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (v.removable) Icons.Outlined.Usb else Icons.Outlined.PhoneAndroid, null, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.label, fontSize = 16.sp)
                                Text(listOfNotNull(v.fs, "${(used * 100 / total.coerceAtLeast(1))}% used").joinToString(" · "), fontSize = 13.sp, color = Accent)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${formatSize(used)} / ${formatSize(total)}", fontSize = 12.sp)
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { used.toFloat() / total.coerceAtLeast(1) }, modifier = Modifier.width(110.dp).height(8.dp).clip(CircleShape),
                                    color = Color(0xFF3FB59B), trackColor = Chip, drawStopIndicator = {},
                                )
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
        SearchPill(onSearch, onVoice, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp))
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(io.github.ibuds.sfiles.Card)) { content() }
}

@Composable
private fun SectionLabel(t: String) =
    Text(t, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp))

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
