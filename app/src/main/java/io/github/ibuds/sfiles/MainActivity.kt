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
        installCrashLog()
        Fs.init(this)
        Thread { ConnStore.all(this) }.start() // warm up the encrypted store off the main thread
        setContent {
            SFilesTheme {
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    if (granted) App(volTick) else PermissionGate()
                    CrashReportDialog()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        granted = hasAllFiles()
        if (granted && android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            !getPreferences(MODE_PRIVATE).getBoolean("askedNotif", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("askedNotif", true).apply()
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2) // so copy progress can show in the shade
        }
        Fs.refresh(this); volTick++
        val f = android.content.IntentFilter().apply {
            listOf(Intent.ACTION_MEDIA_MOUNTED, Intent.ACTION_MEDIA_UNMOUNTED, Intent.ACTION_MEDIA_EJECT, Intent.ACTION_MEDIA_REMOVED).forEach(::addAction)
            addDataScheme("file")
        }
        androidx.core.content.ContextCompat.registerReceiver(this, volReceiver, f, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
    }

    /** Saves the stack trace of a crash so the next launch can show it, since not every phone makes logs easy to get. */
    private fun installCrashLog() {
        val file = File(filesDir, "crash.txt")
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                file.writeText("SFiles ${packageManager.getPackageInfo(packageName, 0).versionName}\n${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\nThread: ${t.name}\n\n" + android.util.Log.getStackTraceString(e))
            }
            old?.uncaughtException(t, e)
        }
    }

    private fun hasAllFiles(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE).all {
            checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        granted = hasAllFiles()
        Fs.refresh(this); volTick++
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
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            } else {
                (ctx as android.app.Activity).requestPermissions(arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            }
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
    BackHandler(stack.size > 1) { stack.removeAt(stack.lastIndex) }

    fun openFolder(f: File?) {
        if (f == null) { while (stack.size > 1) stack.removeAt(stack.lastIndex); return }
        val target = Screen.Folder(f.absolutePath)
        val idx = stack.indexOf(target)
        if (idx >= 0) while (stack.lastIndex > idx) stack.removeAt(stack.lastIndex) else stack.add(target)
    }

    var prevSize by remember { mutableIntStateOf(stack.size) }
    val forward = stack.size >= prevSize
    SideEffect { prevSize = stack.size }
    Box(Modifier.fillMaxSize()) {
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
            onFavorites = { stack.add(Screen.Cat(Category.Favorites)) },
            onTrash = { stack.add(Screen.Trash) },
            onNetwork = { stack.add(Screen.Network) },
            onSettings = { stack.add(Screen.Settings) },
            onCategory = { stack.add(Screen.Cat(it)) },
            onFolder = { openFolder(it) },
            onOpenFile = { openFile(ctx, it) },
            onSearch = { stack.add(Screen.Search()) },
        )
        Screen.Trash -> TrashScreen { stack.removeAt(stack.lastIndex) }
        Screen.Network -> NetworkScreen(
            { stack.removeAt(stack.lastIndex) },
            onOpen = { stack.add(Screen.Remote(it.id)) },
        )
        is Screen.Remote -> ConnStore.get(ctx, s.id)?.let { RemoteBrowserScreen(it, { stack.add(Screen.Settings) }) { stack.removeAt(stack.lastIndex) } }
        Screen.Settings -> SettingsScreen { stack.removeAt(stack.lastIndex) }
        is Screen.Search -> SearchScreen(s.q, { stack.removeAt(stack.lastIndex) }) { openFolder(it) }
        is Screen.Folder -> BrowserScreen(File(s.path), null, ::openFolder, { stack.removeAt(stack.lastIndex) }, { stack.add(Screen.Search()) }, { stack.add(Screen.Settings) }, startVoice)
        is Screen.Cat -> BrowserScreen(null, s.c, ::openFolder, { stack.removeAt(stack.lastIndex) }, { stack.add(Screen.Search()) }, { stack.add(Screen.Settings) }, startVoice)
    }
        }
    }
    if (Ops.tasks.any { !it.hidden }) Column(
        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { Ops.tasks.filter { !it.hidden }.forEach { t -> OpProgressCard(t, { Ops.cancel(t) }) } }
    }
}

/** After a crash, offers the saved stack trace for sharing. */
@Composable
private fun CrashReportDialog() {
    val ctx = LocalContext.current
    val file = remember { File(ctx.filesDir, "crash.txt") }
    var text by remember { mutableStateOf(runCatching { if (file.exists()) file.readText() else null }.getOrNull()) }
    val t = text ?: return
    AppDialog(
        onDismissRequest = { file.delete(); text = null }, containerColor = Sheet,
        title = { Text("SFiles crashed last time") },
        text = { Text("Share the error details so it can be fixed?\n\n" + t.lineSequence().take(4).joinToString("\n"), fontSize = 14.sp) },
        confirmButton = {
            androidx.compose.material3.TextButton({
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "Share crash details"))
                file.delete(); text = null
            }) { Text("Share") }
        },
        dismissButton = { androidx.compose.material3.TextButton({ file.delete(); text = null }) { Text("Dismiss") } },
    )
}
