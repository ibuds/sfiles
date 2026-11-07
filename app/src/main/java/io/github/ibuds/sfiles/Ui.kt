package io.github.ibuds.sfiles

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import java.io.File

private val folderTab = androidx.compose.ui.graphics.vector.PathParser()
    .parsePathString("M5,0 H23 L28,8 H1 V4 A4,4 0 0 1 5,0 Z").toPath()
private val folderBody = androidx.compose.ui.graphics.vector.PathParser()
    .parsePathString("M5,5 H51 A5,5 0 0 1 56,10 V37 A5,5 0 0 1 51,42 H5 A5,5 0 0 1 0,37 V10 A5,5 0 0 1 5,5 Z").toPath()
private val folderStripe = androidx.compose.ui.graphics.vector.PathParser()
    .parsePathString("M9,12 H47 A2,2 0 0 1 47,16 H9 A2,2 0 0 1 9,12 Z").toPath()

/** Same geometry and colours as the app icon (56 x 42 design units). */
@Composable
fun FolderIcon(file: File? = null, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 48.dp, height = 36.dp)) {
        val k = size.width / 56f
        scale(k, k, pivot = Offset.Zero) {
            drawPath(folderTab, Color(0xFFE8A200))
            drawPath(folderBody, Color(0xFFFFC21A))
            drawPath(folderStripe, Color(0xFFFFE98A))
        }
    }
}

@Composable
fun SearchPill(onClick: () -> Unit, onVoice: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier.width(280.dp).shadow(ShadowElev, CircleShape).background(Bar).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = Muted)
        Spacer(Modifier.width(10.dp))
        Text("Search", color = Muted, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (onVoice != null) Icon(Icons.Filled.Mic, "Voice search", tint = Muted, modifier = Modifier.clickable { onVoice() })
    }
}

/** Long-press a row, then drag up or down to select a range. Edge-scrolls while dragging. */
@Composable
fun Modifier.dragSelect(
    state: androidx.compose.foundation.lazy.LazyListState, paths: List<String>,
    selected: Set<String>, onChange: (Set<String>) -> Unit, onStart: () -> Unit,
): Modifier {
    val curPaths by androidx.compose.runtime.rememberUpdatedState(paths)
    val curSel by androidx.compose.runtime.rememberUpdatedState(selected)
    val curChange by androidx.compose.runtime.rememberUpdatedState(onChange)
    val curStart by androidx.compose.runtime.rememberUpdatedState(onStart)
    return this.pointerInput(state) {
        fun indexAt(y: Float): Int =
            state.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }?.index ?: -1
        awaitEachGesture {
            // Watch in the Initial pass so we see touches before the list's scroll handling does.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            val timedOut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val c = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull Unit
                    if (c.changedToUp() || (c.position - down.position).getDistance() > slop) return@withTimeoutOrNull Unit
                }
            } == null
            if (!timedOut) return@awaitEachGesture

            val anchor = indexAt(down.position.y)
            if (anchor !in curPaths.indices) return@awaitEachGesture
            curStart()
            val base = curSel
            curChange(base + curPaths[anchor])
            var lastY = down.position.y
            val h = size.height.toFloat()
            while (true) {
                val ev = withTimeoutOrNull(16) { awaitPointerEvent(PointerEventPass.Initial) }
                if (ev != null) {
                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                    c.consume() // keep the list from scrolling and the row from treating this as a tap
                    if (!c.pressed) break
                    lastY = c.position.y
                }
                if (lastY < 140f) state.dispatchRawDelta(-28f) else if (lastY > h - 140f) state.dispatchRawDelta(28f)
                val i = indexAt(lastY.coerceIn(0f, h - 1f))
                if (i >= 0) {
                    val r = if (i >= anchor) anchor..i else i..anchor
                    curChange(base + r.mapNotNull { curPaths.getOrNull(it) })
                }
            }
        }
    }
}

private val archiveExt = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")

@Composable
fun Thumb(file: File, modifier: Modifier = Modifier, remote: Boolean = false) {
    val ctx = LocalContext.current
    val kind = file.kind()
    val shape = RoundedCornerShape(8.dp)
    val (icon, tint) = when {
        kind == Kind.Images -> Icons.Outlined.Image to Color(0xFFE8657F)
        kind == Kind.Videos -> Icons.Outlined.PlayArrow to Color(0xFFB07CF5)
        kind == Kind.Audio -> Icons.Outlined.MusicNote to Color(0xFF8A93F0)
        kind == Kind.Docs -> Icons.Outlined.Description to Color(0xFFF0B860)
        kind == Kind.Apk -> Icons.Outlined.Android to Color(0xFFA4D93A)
        file.extension.lowercase() in archiveExt -> Icons.Outlined.FolderZip to Color(0xFF8E9AAF)
        else -> Icons.Outlined.InsertDriveFile to Color(0xFF8E9AAF)
    }
    // The icon sits underneath, so it shows while a thumbnail loads and whenever one can't be made.
    Box(modifier.size(48.dp).clip(shape).background(Tile), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.fillMaxSize(0.54f))
        if (!remote && (kind == Kind.Images || kind == Kind.Videos)) {
            val req = androidx.compose.runtime.remember(file) {
                ImageRequest.Builder(ctx).data(file).size(160)
                    .apply { if (kind == Kind.Videos) decoderFactory(VideoFrameDecoder.Factory()) }.build()
            }
            AsyncImage(req, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryRow(
    e: Entry, selectMode: Boolean, selected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, subtitle: String? = null,
    remote: Boolean = false, countText: String? = null, modifier: Modifier = Modifier,
) {
    Column(modifier) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 24.dp).height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            selectMode,
            enter = expandHorizontally(tween(220)) + fadeIn(tween(220)),
            exit = shrinkHorizontally(tween(180)) + fadeOut(tween(150)),
        ) { Row(verticalAlignment = Alignment.CenterVertically) { Check(selected); Spacer(Modifier.width(14.dp)) } }
        Box(Modifier.width(48.dp).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            if (e.isDir) FolderIcon(if (remote) null else e.file) else Thumb(e.file, remote = remote)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
            Text(e.label, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle ?: formatDate(e.modified), fontSize = 13.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            countText?.let { if (e.isDir) it else formatSize(e.size) } ?: if (e.isDir) formatItems(e.count) else formatSize(e.size),
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Muted,
        )
    }
    val dividerStart by androidx.compose.animation.core.animateDpAsState(if (selectMode) 94.dp else 80.dp, tween(220), label = "divider")
    androidx.compose.material3.HorizontalDivider(
        Modifier.padding(start = dividerStart, end = 24.dp), thickness = 0.7.dp, color = Divider,
    )
    }
}

@Composable
fun Check(checked: Boolean) {
    val bg by animateColorAsState(if (checked) Accent else Color.Transparent, tween(160), label = "check-bg")
    val ring by androidx.compose.animation.core.animateFloatAsState(if (checked) 0f else 1f, tween(160), label = "check-ring")
    Box(Modifier.size(24.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFF9A9A9A).copy(alpha = ring), radius = size.minDimension / 2 - 1.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx()))
        }
        AnimatedVisibility(checked, enter = scaleIn(tween(160)) + fadeIn(tween(120)), exit = scaleOut(tween(120)) + fadeOut(tween(100))) {
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

/** Pill breadcrumb: home > Internal storage > DCIM */
@Composable
fun Breadcrumb(dir: File, onNavigate: (File?) -> Unit, modifier: Modifier = Modifier, bg: Color = Pill) {
    val vol = Fs.volumeOf(dir)
    val parts = ArrayList<File>()
    var f: File? = dir
    while (f != null && f.absolutePath.length > vol.dir.absolutePath.length) { parts.add(0, f); f = f.parentFile }
    Row(
        modifier.fillMaxWidth().clip(CircleShape).background(bg).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Home, "Home", tint = Muted, modifier = Modifier.size(22.dp).clickable { onNavigate(null) })
        Sep()
        val rootLast = parts.isEmpty()
        Text(
            vol.label, fontSize = 15.sp,
            color = if (rootLast) Accent else Muted, fontWeight = if (rootLast) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.clickable { onNavigate(vol.dir) },
        )
        parts.forEachIndexed { i, p ->
            Sep()
            val last = i == parts.lastIndex
            Text(
                p.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (last) Accent else Muted, fontWeight = if (last) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onNavigate(p) },
            )
        }
    }
}

@Composable
private fun Sep() = Icon(Icons.Outlined.ChevronRight, null, tint = Muted, modifier = Modifier.padding(horizontal = 6.dp).size(18.dp))
