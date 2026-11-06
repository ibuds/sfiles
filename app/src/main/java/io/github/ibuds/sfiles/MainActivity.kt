package io.github.ibuds.sfiles

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

sealed interface Screen {
    object Home : Screen
    data class Search(val q: String = "") : Screen
    object Trash : Screen
    object Network : Screen
    object Settings : Screen
    data class Remote(val id: String) : Screen
    data class ConnForm(val proto: Proto, val id: String?) : Screen
    data class Folder(val path: String) : Screen
    data class Cat(val c: Category) : Screen
}

class MainActivity : ComponentActivity() {
    private var granted by mutableStateOf(false)
    private var volTick by mutableIntStateOf(0)
    private val volReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: Intent) { Fs.refresh(this@MainActivity); volTick++ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Fs.init(this)
        Thread { ConnStore.all(this) }.start() // warm up the encrypted store off the main thread
        setContent {
            SFilesTheme {
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    if (granted) App(volTick) else PermissionGate()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        granted = Environment.isExternalStorageManager()
        Fs.refresh(this); volTick++
        val f = android.content.IntentFilter().apply {
            listOf(Intent.ACTION_MEDIA_MOUNTED, Intent.ACTION_MEDIA_UNMOUNTED, Intent.ACTION_MEDIA_EJECT, Intent.ACTION_MEDIA_REMOVED).forEach(::addAction)
            addDataScheme("file")
        }
        androidx.core.content.ContextCompat.registerReceiver(this, volReceiver, f, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onPause() { super.onPause(); runCatching { unregisterReceiver(volReceiver) } }
}

@Composable
private fun PermissionGate() {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("Allow access to all files", fontSize = 22.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("SFiles needs this permission to browse and manage the files on your phone.", textAlign = TextAlign.Center, color = Muted)
        Spacer(Modifier.height(24.dp))
        Button({
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")))
        }) { Text("Continue") }
    }
}

@Composable
private fun App(volTick: Int) {
    val ctx = LocalContext.current
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val voice = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) stack.add(Screen.Search(text))
    }
    val startVoice: () -> Unit = {
        try {
            voice.launch(Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
        } catch (_: Exception) { android.widget.Toast.makeText(ctx, "Voice search isn't available", android.widget.Toast.LENGTH_SHORT).show() }
    }
    BackHandler(stack.size > 1) { stack.removeLast() }

    fun openFolder(f: File?) {
        if (f == null) { while (stack.size > 1) stack.removeLast(); return }
        val target = Screen.Folder(f.absolutePath)
        val idx = stack.indexOf(target)
        if (idx >= 0) while (stack.lastIndex > idx) stack.removeLast() else stack.add(target)
    }

    var prevSize by remember { mutableIntStateOf(stack.size) }
    val forward = stack.size >= prevSize
    SideEffect { prevSize = stack.size }
    AnimatedContent(
        targetState = stack.last(),
        transitionSpec = {
            val spec = tween<androidx.compose.ui.unit.IntOffset>(240, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            val fade = tween<Float>(180)
            if (forward) (slideInHorizontally(spec) { it / 3 } + fadeIn(fade)) togetherWith (slideOutHorizontally(spec) { -it / 6 } + fadeOut(fade))
            else (slideInHorizontally(spec) { -it / 6 } + fadeIn(fade)) togetherWith (slideOutHorizontally(spec) { it / 3 } + fadeOut(fade))
        },
        label = "screens",
    ) { s ->
        Box(Modifier.fillMaxSize().background(Bg)) {
        when (s) {
        Screen.Home -> HomeScreen(
            volTick,
            onVoice = startVoice,
            onRecent = { stack.add(Screen.Cat(Category.Recent)) },
            onTrash = { stack.add(Screen.Trash) },
            onNetwork = { stack.add(Screen.Network) },
            onSettings = { stack.add(Screen.Settings) },
            onCategory = { stack.add(Screen.Cat(it)) },
            onFolder = { openFolder(it) },
            onOpenFile = { openFile(ctx, it) },
            onSearch = { stack.add(Screen.Search()) },
        )
        Screen.Trash -> TrashScreen { stack.removeLast() }
        Screen.Network -> NetworkScreen(
            { stack.removeLast() },
            onOpen = { stack.add(Screen.Remote(it.id)) },
            onAdd = { stack.add(Screen.ConnForm(it, null)) },
            onEdit = { stack.add(Screen.ConnForm(it.proto, it.id)) },
        )
        is Screen.Remote -> ConnStore.get(ctx, s.id)?.let { RemoteBrowserScreen(it) { stack.removeLast() } }
        is Screen.ConnForm -> ConnFormScreen(s.proto, s.id?.let { ConnStore.get(ctx, it) }) { stack.removeLast() }
        Screen.Settings -> SettingsScreen { stack.removeLast() }
        is Screen.Search -> SearchScreen(s.q, { stack.removeLast() }) { openFolder(it) }
        is Screen.Folder -> BrowserScreen(File(s.path), null, ::openFolder, { stack.removeLast() }, { stack.add(Screen.Search()) }, { stack.add(Screen.Settings) }, startVoice)
        is Screen.Cat -> BrowserScreen(null, s.c, ::openFolder, { stack.removeLast() }, { stack.add(Screen.Search()) }, { stack.add(Screen.Settings) }, startVoice)
    }
        }
    }
}
