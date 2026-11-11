package io.github.ibuds.sfiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ChevronRight
import android.content.Context
import android.content.Intent
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff

// Shared building blocks, so every screen uses identical sizes, spacing and colours.

/** One shadow depth for everything that floats: menus, the search pill, the action bar, the progress pill. */
val ShadowElev = 8.dp

/** Soft, low-contrast shadow. The default Material shadow looks heavy on light backgrounds. */
fun Modifier.softShadow(shape: androidx.compose.ui.graphics.Shape) =
    shadow(ShadowElev, shape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.16f))

/** Dropdown with the app's shape, colour, shadow and comfortable spacing. */
@Composable
fun AppMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) =
    DropdownMenu(
        expanded, onDismiss, modifier = Modifier.widthIn(min = 220.dp), shape = RoundedCornerShape(24.dp), containerColor = MenuBg,
        tonalElevation = 0.dp, shadowElevation = 2.dp, content = content,
    )

/** Back arrow + title + optional trailing actions. Icons sit 20dp from the screen edge. */
@Composable
fun ScreenHeader(title: String?, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        if (title != null) Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** The three-dot button with its dropdown. [content] receives a function that closes the menu. */
@Composable
fun OverflowMenu(content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }) { Icon(Icons.Filled.MoreVert, "More options") }
        AppMenu(open, { open = false }) { content { open = false } }
    }
}

@Composable
fun MenuItem(label: String, onClick: () -> Unit) =
    androidx.compose.material3.DropdownMenuItem(
        { Text(label, fontSize = 17.sp) }, onClick,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    )

/** Pill showing "home > label" for top-level screens such as Trash and Network storage. */
@Composable
fun HomeCrumb(label: String, onHome: () -> Unit) = Crumbs(listOf(label), onHome, {}, Modifier.padding(horizontal = 16.dp))

/**
 * Breadcrumb pill: home > names[0] > names[1] ... Long paths scroll sideways, and the pill stays scrolled to the end
 * so the current folder is always in view.
 */
@Composable
fun Crumbs(names: List<String>, onHome: () -> Unit, onPick: (Int) -> Unit, modifier: Modifier = Modifier, bg: Color = Pill) {
    val scroll = rememberScrollState()
    LaunchedEffect(names) { scroll.scrollTo(scroll.maxValue) }
    Row(
        modifier.fillMaxWidth().clip(CircleShape).background(bg).horizontalScroll(scroll).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Home, "Home", tint = Muted, modifier = Modifier.size(22.dp).clickable { onHome() })
        names.forEachIndexed { i, n ->
            Icon(Icons.Outlined.ChevronRight, null, tint = Muted, modifier = Modifier.padding(horizontal = 6.dp).size(18.dp))
            val cur = i == names.lastIndex
            Text(
                n, fontSize = 15.sp, maxLines = 1, softWrap = false,
                color = if (cur) Accent else Muted, fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onPick(i) },
            )
        }
    }
}

/** Every dialog in the app: a rounded card floating at the bottom of the screen, above the keyboard. */
@Composable
fun AppSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismiss, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val none = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        Box(
            Modifier.fillMaxSize().clickable(none, null, onClick = onDismiss).systemBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Sheet)
                    .clickable(none, null) {} // taps on the card must not dismiss it
                    .verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp),
                content = content,
            )
        }
    }
}

/** Two equal text buttons split by a thin divider (or just one). Disabled buttons fade out. */
@Composable
fun DialogButtonRow(left: (@Composable () -> Unit)?, right: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ty = MaterialTheme.typography
    MaterialTheme(colorScheme = cs.copy(primary = cs.onSurface), typography = ty.copy(labelLarge = ty.labelLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (left != null) {
                Box(Modifier.weight(1f).heightIn(min = 56.dp), Alignment.Center) { left() }
                Box(Modifier.width(1.dp).height(20.dp).background(Divider))
            }
            Box(Modifier.weight(1f).heightIn(min = 56.dp), Alignment.Center) { right() }
        }
    }
}

/**
 * Underlined text field used everywhere (dialogs and forms). Text starts flush with the dialog's edge.
 * [selectAll] selects the whole text on open and [autoFocus] opens the keyboard, like a rename box.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTextField(
    value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    label: String? = null, placeholder: String? = null, enabled: Boolean = true,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    secret: Boolean = false, autoFocus: Boolean = false, selectAll: Boolean = false,
    suffix: (@Composable () -> Unit)? = null, supporting: (@Composable () -> Unit)? = null,
) {
    var tfv by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(value, androidx.compose.ui.text.TextRange(if (selectAll) 0 else value.length, value.length))) }
    val shown = if (tfv.text == value) tfv else androidx.compose.ui.text.input.TextFieldValue(value, androidx.compose.ui.text.TextRange(value.length))
    var visible by remember { mutableStateOf(false) }
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
    val fg = MaterialTheme.colorScheme.onSurface
    val colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = fg, unfocusedIndicatorColor = Divider, disabledIndicatorColor = Divider,
        focusedLabelColor = Accent, unfocusedLabelColor = Accent, disabledLabelColor = Muted,
        focusedSuffixColor = Muted, unfocusedSuffixColor = Muted, cursorColor = fg,
    )
    val vt = if (secret && !visible) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
    androidx.compose.foundation.text.BasicTextField(
        value = shown, onValueChange = { tfv = it; if (it.text != value) onChange(it.text) },
        modifier = modifier.fillMaxWidth().focusRequester(focus), enabled = enabled, singleLine = true,
        textStyle = LocalTextStyle.current.copy(color = if (enabled) fg else Muted, fontSize = 17.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(fg),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = vt, interactionSource = interaction,
        decorationBox = { inner ->
            TextFieldDefaults.DecorationBox(
                value = shown.text, innerTextField = inner, enabled = enabled, singleLine = true, visualTransformation = vt,
                interactionSource = interaction, colors = colors,
                label = label?.let { { Text(it) } }, placeholder = placeholder?.let { { Text(it, color = Muted) } },
                suffix = suffix, supportingText = supporting,
                trailingIcon = if (secret) ({
                    IconButton({ visible = !visible }) {
                        Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Hide password" else "Show password")
                    }
                }) else null,
                contentPadding = if (label != null) TextFieldDefaults.contentPaddingWithLabel(start = 0.dp, end = 0.dp, top = 8.dp, bottom = 8.dp)
                    else TextFieldDefaults.contentPaddingWithoutLabel(start = 0.dp, end = 0.dp, top = 8.dp, bottom = 8.dp),
                container = { TextFieldDefaults.Container(enabled, false, interaction, colors = colors, shape = RectangleShape) },
            )
        },
    )
}

/** Title, message and buttons on an [AppSheet]. Takes the same parameters as Material's AlertDialog. */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = Color.Unspecified,
    shape: Shape = RectangleShape,
) = AppSheet(onDismissRequest) {
    if (title != null) Box(Modifier.padding(bottom = 16.dp)) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold)) { title() }
    }
    if (text != null) Box(Modifier.fillMaxWidth().padding(bottom = 12.dp)) { ProvideTextStyle(MaterialTheme.typography.bodyLarge) { text() } }
    DialogButtonRow(dismissButton, confirmButton)
}

/** "3 selected" pill with select-all, and Cancel. */
@Composable
fun SelectionTopBar(count: Int, allSelected: Boolean, onToggleAll: () -> Unit, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(CircleShape).background(Pill).clickable(onClick = onToggleAll).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Check(allSelected)
            Spacer(Modifier.width(12.dp))
            Text("$count", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Cancel", fontSize = 18.sp,
            modifier = Modifier.clip(CircleShape).background(Pill).clickable(onClick = onCancel).padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

/** Floating bottom bar shown while items are selected. */
@Composable
fun ActionBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
            .fillMaxWidth().softShadow(CircleShape).background(Bar).padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        content = content,
    )
}

@Composable
fun RowScope.BarAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    // Every action gets an equal-width slot, so the icons are evenly spaced whatever their label length.
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val c = if (enabled) MaterialTheme.colorScheme.onSurface else Muted.copy(alpha = 0.5f)
        Icon(icon, null, tint = c, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 12.sp, color = c)
    }
}

@Composable
fun EmptyState(title: String, body: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text(title, fontSize = 20.sp, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(12.dp))
            Text(body, fontSize = 15.sp, color = Muted, textAlign = TextAlign.Center)
        }
        if (action != null) { Spacer(Modifier.height(24.dp)); action() }
    }
}

fun itemsText(n: Int) = if (n == 1) "1 item" else "$n items"

/** Small grey heading above a group of cards. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Muted, modifier = modifier)

/** Folder path for list subtitles, without the redundant "/Internal storage" prefix. */
fun shortPath(dir: java.io.File): String = Fs.displayPath(dir).removePrefix("/Internal storage").ifEmpty { "/" }

/**
 * Plain progress bar: a rounded track with the fill clipped to it, so only the outer ends are rounded and the fill's
 * leading edge is straight. A null [progress] shows a sliding segment.
 */
@Composable
fun AppProgressBar(progress: Float?, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = Accent) {
    Box(modifier.clip(CircleShape).background(Chip)) {
        if (progress != null) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(color))
        } else {
            val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "indeterminate")
            val x by t.animateFloat(
                -0.35f, 1f,
                androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.LinearEasing)),
                label = "x",
            )
            Box(Modifier.fillMaxHeight().fillMaxWidth(0.35f).graphicsLayer { translationX = x * size.width / 0.35f }.background(color))
        }
    }
}

/** Floating card for one running operation: title and percentage, a progress bar, details, then Hide and Cancel below the bar. */
@Composable
fun OpProgressCard(t: OpTask, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().softShadow(RoundedCornerShape(24.dp)).background(Bar).padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
    ) {
        Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
            if (t.total > 0) Text("${t.percent}%", fontSize = 14.sp, color = Muted)
        }
        Spacer(Modifier.height(10.dp))
        AppProgressBar(if (t.total > 0) t.percent / 100f else null, Modifier.padding(end = 8.dp).fillMaxWidth().height(6.dp))
        val d = t.detail()
        val eta = t.etaMs()?.let(::formatEta)
        if (d.isNotEmpty() || eta != null) {
            Spacer(Modifier.height(6.dp))
            Text(listOfNotNull(d.ifEmpty { null }, eta).joinToString(" · "), fontSize = 12.sp, color = Muted, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            listOf("Hide" to { t.hidden = true }, "Cancel" to onCancel).forEach { (label, action) ->
                Text(
                    label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Accent,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = action).padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Opens the system storage settings, where Android lets the user eject or format a drive. */
fun openDriveSettings(ctx: Context) {
    val tries = listOf("android.settings.MEMORY_CARD_SETTINGS", android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS, android.provider.Settings.ACTION_SETTINGS)
    for (a in tries) { try { ctx.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) {} }
}

/** Unmounting and formatting are reserved for the system, so SFiles hands over to Settings. */
@Composable
fun DriveSettingsDialog(unmount: Boolean, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    AppDialog(
        onDismissRequest = onDismiss, containerColor = Sheet, shape = RoundedCornerShape(28.dp),
        title = { Text(if (unmount) "Unmount drive" else "Format drive", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                if (unmount) "Android only lets the system Settings app eject drives, so SFiles can't do it directly. Open storage settings and tap the eject button next to the drive."
                else "Android only lets the system Settings app format drives, so SFiles can't do it directly. Formatting erases everything on the drive.",
            )
        },
        confirmButton = { TextButton({ onDismiss(); openDriveSettings(ctx) }) { Text("Open settings") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
