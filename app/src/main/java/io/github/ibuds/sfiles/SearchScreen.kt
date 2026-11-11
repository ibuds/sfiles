package io.github.ibuds.sfiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(initial: String, onBack: () -> Unit, onFolder: (File) -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    var searching by remember { mutableStateOf(false) }
    val results by produceState(emptyList<Entry>(), q) {
        searching = true
        delay(250)
        value = withContext(Dispatchers.IO) { Fs.search(q) }
        searching = false
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            TextField(
                q, { q = it }, singleLine = true, placeholder = { Text("Search") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { Fs.addHistory(q) }),
                modifier = Modifier.weight(1f).focusRequester(focus).clip(CircleShape),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Pill, unfocusedContainerColor = Pill,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
        }
        val recents by produceState(emptyList<Entry>(), Fs.recentInSearch) { value = if (Fs.recentInSearch) withContext(Dispatchers.IO) { Fs.recent(ctx, 8) } else emptyList() }
        if (q.isBlank()) {
            val hist = if (Fs.historyOn) Fs.history else emptyList()
            if (hist.isEmpty() && recents.isEmpty()) EmptyState("Search your files", "Type a name to look through everything on this device.")
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (hist.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle("Search history", Modifier.weight(1f))
                            Text("Clear", fontSize = 14.sp, color = Accent, modifier = Modifier.clip(CircleShape).clickable { Fs.removeHistory(null) }.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                    items(hist, key = { "h$it" }) { h ->
                        Row(Modifier.fillMaxWidth().clickable { q = h }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.History, null, tint = Muted)
                            Spacer(Modifier.width(16.dp))
                            Text(h, fontSize = 17.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Icon(Icons.Filled.Close, "Remove", tint = Muted, modifier = Modifier.size(20.dp).clickable { Fs.removeHistory(h) })
                        }
                    }
                }
                if (recents.isNotEmpty()) {
                    item { SectionTitle("Recent files", Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp)) }
                    items(recents, key = { "r${it.path}" }) { e ->
                        EntryRow(e, false, false, onClick = { openFile(ctx, e.file) }, onLongClick = {}, subtitle = e.file.parentFile?.let(::shortPath))
                    }
                }
            }
        }
        else if (results.isEmpty() && !searching) EmptyState("No results", "Nothing matches \"$q\".")
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(results, key = { it.path }) { e ->
                EntryRow(
                    e, false, false,
                    onClick = { Fs.addHistory(q); if (e.isDir) onFolder(e.file) else openFile(ctx, e.file) },
                    onLongClick = {},
                    subtitle = e.file.parentFile?.let(::shortPath), modifier = Modifier.animateItem())
            }
        }
    }
}
