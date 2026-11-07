package io.github.ibuds.sfiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
                modifier = Modifier.weight(1f).focusRequester(focus).clip(CircleShape),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Pill, unfocusedContainerColor = Pill,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
        }
        if (q.isBlank()) EmptyState("Search your files", "Type a name to look through everything on this device.")
        else if (results.isEmpty() && !searching) EmptyState("No results", "Nothing matches \"$q\".")
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(results, key = { it.path }) { e ->
                EntryRow(
                    e, false, false,
                    onClick = { if (e.isDir) onFolder(e.file) else openFile(ctx, e.file) },
                    onLongClick = {},
                    subtitle = e.file.parentFile?.let(::shortPath), modifier = Modifier.animateItem())
            }
        }
    }
}
