package io.github.ibuds.sfiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPicker(start: File, actionLabel: String, onDismiss: () -> Unit, onPicked: (File) -> Unit) {
    var cur by remember { mutableStateOf(start) }
    var tick by remember { mutableIntStateOf(0) }
    var newFolder by remember { mutableStateOf(false) }
    val dirs = remember(cur, tick) { Fs.list(cur, SortBy.Name, true, Kind.All).filter { it.isDir } }

    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = Sheet,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.fillMaxHeight(0.9f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Select folder", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton({ newFolder = true }) { Icon(Icons.Outlined.CreateNewFolder, null) }
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val curVol = Fs.volumeOf(cur)
                Fs.vols.forEach { v ->
                    Text(
                        v.label, fontSize = 15.sp,
                        modifier = Modifier.clip(CircleShape)
                            .background(if (v == curVol) Chip else Color.Transparent)
                            .clickable { cur = v.dir }.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            Breadcrumb(cur, { cur = it ?: Fs.root }, Modifier.padding(horizontal = 20.dp), bg = Chip)
            LazyColumn(Modifier.weight(1f)) {
                items(dirs, key = { it.path }) { e ->
                    EntryRow(e, false, false, onClick = { cur = e.file }, onLongClick = {})
                }
            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onDismiss, Modifier.weight(1f)) { Text("Cancel", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
                TextButton({ onPicked(cur) }, Modifier.weight(1f)) { Text(actionLabel, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
            }
        }
    }
    if (newFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolder = false }, containerColor = Sheet,
            title = { Text("Create folder") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Folder name") }) },
            confirmButton = { TextButton({ if (name.isNotBlank()) File(cur, name).mkdirs(); newFolder = false; tick++ }) { Text("Create") } },
            dismissButton = { TextButton({ newFolder = false }) { Text("Cancel") } },
        )
    }
}
