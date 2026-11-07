package io.github.ibuds.sfiles

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class SortBy(val label: String) { Name("Name"), Type("Type"), Date("Date"), Size("Size") }
enum class Kind(val label: String) { All("All"), Images("Images"), Videos("Videos"), Audio("Audio"), Docs("Documents"), Apk("Install files"), Other("Other") }
enum class Category(val label: String) { Downloads("Downloads"), Docs("Docs"), Images("Images"), Audio("Audio"), Videos("Videos"), Apk("Install files"), Recent("Recent files"), Favorites("Favorites") }

data class Entry(val file: File, val isDir: Boolean, val modified: Long, val size: Long, val count: Int) {
    val name: String get() = file.name
    val path: String get() = file.absolutePath
}

fun File.kind(): Kind = when (extension.lowercase()) {
    "jpg", "jpeg", "png", "webp", "gif", "heic", "bmp" -> Kind.Images
    "mp4", "mkv", "mov", "3gp", "webm" -> Kind.Videos
    "mp3", "m4a", "wav", "flac", "ogg", "aac", "opus" -> Kind.Audio
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf" -> Kind.Docs
    "apk" -> Kind.Apk
    else -> Kind.Other
}

object Fs {
    val root: File = Environment.getExternalStorageDirectory()
    val trashDir: File get() = File(root, ".SFilesTrash")

    private var prefs: android.content.SharedPreferences? = null
    var showHidden by androidx.compose.runtime.mutableStateOf(false)
    /** 0 = follow device, 1 = light, 2 = dark */
    var themeMode by androidx.compose.runtime.mutableIntStateOf(0)
    fun updateThemeMode(v: Int) { themeMode = v; prefs?.edit()?.putInt("themeMode", v)?.apply() }
    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("sfiles", Context.MODE_PRIVATE)
        themeMode = prefs!!.getInt("themeMode", 0)
        showHidden = prefs!!.getBoolean("showHidden", false)
        hideExt = prefs!!.getBoolean("hideExt", false)
        favorites = prefs!!.getString("favorites", "")!!.split("\n").filter { it.isNotEmpty() }
    }
    fun updateShowHidden(v: Boolean) { showHidden = v; prefs?.edit()?.putBoolean("showHidden", v)?.apply() }

    var hideExt by androidx.compose.runtime.mutableStateOf(false)
    fun updateHideExt(v: Boolean) { hideExt = v; prefs?.edit()?.putBoolean("hideExt", v)?.apply() }

    /** Favourite file and folder paths, newest first. */
    var favorites by androidx.compose.runtime.mutableStateOf(listOf<String>())
    fun setFavorite(paths: Collection<String>, on: Boolean) {
        favorites = if (on) paths.filter { it !in favorites } + favorites else favorites - paths.toSet()
        prefs?.edit()?.putString("favorites", favorites.joinToString("\n"))?.apply()
    }
    fun favoriteEntries(): List<Entry> = favorites.map(::File)
        .filter { it.exists() && (showHidden || !it.name.startsWith(".")) }.map(::entryOf)

    data class TrashItem(val entry: Entry, val original: File, val deletedAt: Long)
    private fun records(): MutableSet<String> = (prefs?.getStringSet("trash", emptySet()) ?: emptySet()).toMutableSet()
    private fun saveRecords(s: Set<String>) { prefs?.edit()?.putStringSet("trash", s)?.apply() }

    fun trashItems(): List<TrashItem> = records().mapNotNull { r ->
        val (name, orig, t) = r.split("|", limit = 3).takeIf { it.size == 3 } ?: return@mapNotNull null
        val f = File(trashDir, name)
        if (f.exists()) TrashItem(entryOf(f), File(orig), t.toLongOrNull() ?: 0L) else null
    }.sortedByDescending { it.deletedAt }

    fun restore(item: TrashItem, onBytes: (Long) -> Unit = {}): Boolean {
        val dir = item.original.parentFile ?: root
        dir.mkdirs()
        val ok = move(item.entry.file, dir, onBytes)
        if (ok) saveRecords(records().filterNot { it.startsWith(item.entry.name + "|") }.toSet())
        return ok
    }
    fun deleteForever(item: TrashItem): Boolean {
        val ok = item.entry.file.deleteRecursively()
        saveRecords(records().filterNot { it.startsWith(item.entry.name + "|") }.toSet())
        return ok
    }
    fun purgeOld() {
        val limit = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        trashItems().filter { it.deletedAt < limit }.forEach { deleteForever(it) }
    }

    data class Vol(val dir: File, val label: String, val removable: Boolean, val uuid: String? = null, val fs: String? = null)
    @Volatile var vols: List<Vol> = listOf(Vol(root, "Internal storage", false))

    fun refresh(ctx: Context) {
        val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as android.os.storage.StorageManager
        val found = sm.storageVolumes.filter { it.state == Environment.MEDIA_MOUNTED }.mapNotNull { v ->
            val d = volumeDir(v) ?: return@mapNotNull null
            Vol(d, if (v.isPrimary) "Internal storage" else (v.getDescription(ctx) ?: "USB drive"), !v.isPrimary, v.uuid,
                if (v.isPrimary) null else fsType(v.uuid, d))
        }
        vols = if (found.any { !it.removable }) found else listOf(Vol(root, "Internal storage", false)) + found
    }

    /** StorageVolume.getDirectory() only exists on Android 11+; older versions expose the path through getPath(). */
    @Suppress("DiscouragedPrivateApi")
    private fun volumeDir(v: android.os.storage.StorageVolume): File? =
        if (android.os.Build.VERSION.SDK_INT >= 30) v.directory
        else try { (v.javaClass.getMethod("getPath").invoke(v) as? String)?.let(::File) } catch (_: Exception) { null }

    /** Looks up the filesystem of a removable volume in the mount table (best effort, null if unknown). */
    private fun fsType(uuid: String?, dir: File): String? {
        val lines = listOf("/proc/self/mountinfo", "/proc/mounts").flatMap { p ->
            try { File(p).readLines() } catch (_: Exception) { emptyList() }
        }
        var best: String? = null
        for (l in lines) {
            val hit = (uuid != null && l.contains(uuid, ignoreCase = true)) || l.contains(dir.absolutePath)
            if (!hit) continue
            val t = l.split(" ")
            val type = if (l.contains(" - ")) l.substringAfter(" - ").split(" ").firstOrNull() else t.getOrNull(2)
            val pretty = when (type?.lowercase()) {
                "vfat", "msdos", "fat" -> "FAT32"
                "exfat", "sdfat" -> "exFAT"
                "ntfs", "ntfs3", "fuseblk", "tntfs", "ufsd" -> "NTFS"
                "ext4" -> "ext4"
                "f2fs" -> "F2FS"
                else -> null
            }
            if (pretty != null) { best = pretty; break }
        }
        return best
    }

    fun volumeOf(f: File): Vol {
        val p = f.absolutePath
        return vols.filter { p == it.dir.absolutePath || p.startsWith(it.dir.absolutePath + "/") }
            .maxByOrNull { it.dir.absolutePath.length } ?: vols.first()
    }

    fun isRemovable(f: File) = volumeOf(f).removable

    fun entryOf(f: File) = Entry(
        f, f.isDirectory, f.lastModified(),
        if (f.isFile) f.length() else 0L,
        if (f.isDirectory) (f.list()?.count { !it.startsWith(".") } ?: 0) else 0,
    )

    fun list(dir: File, sort: SortBy, asc: Boolean, filter: Kind): List<Entry> {
        val items = (dir.listFiles() ?: emptyArray())
            .filter { showHidden || !it.name.startsWith(".") }
            .filter { it.isDirectory || filter == Kind.All || it.kind() == filter }
            .map(::entryOf)
        val base: Comparator<Entry> = when (sort) {
            SortBy.Name -> compareBy { it.name.lowercase() }
            SortBy.Type -> compareBy<Entry> { it.file.extension.lowercase() }.thenBy { it.name.lowercase() }
            SortBy.Date -> compareBy { it.modified }
            SortBy.Size -> compareBy { if (it.isDir) it.count.toLong() else it.size }
        }
        val cmp = if (asc) base else base.reversed()
        return items.filter { it.isDir }.sortedWith(cmp) + items.filter { !it.isDir }.sortedWith(cmp)
    }

    fun category(ctx: Context, cat: Category, limit: Int = 500): List<Entry> {
        val uri = MediaStore.Files.getContentUri("external")
        val sel: String
        val args: Array<String>?
        when (cat) {
            Category.Recent -> return recent(ctx, 300)
            Category.Favorites -> return favoriteEntries()
            Category.Images -> { sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=1"; args = null }
            Category.Audio -> { sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=2"; args = null }
            Category.Videos -> { sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=3"; args = null }
            Category.Apk -> { sel = "${MediaStore.Files.FileColumns.MIME_TYPE}='application/vnd.android.package-archive'"; args = null }
            Category.Downloads -> { sel = "${MediaStore.Files.FileColumns.DATA} LIKE ?"; args = arrayOf(File(root, "Download").absolutePath + "/%") }
            Category.Docs -> {
                sel = "${MediaStore.Files.FileColumns.MIME_TYPE} IN ('application/pdf','application/msword','application/vnd.ms-excel','application/vnd.ms-powerpoint','text/plain','text/csv') OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/vnd.openxmlformats%'"
                args = null
            }
        }
        val out = ArrayList<Entry>()
        ctx.contentResolver.query(
            uri, arrayOf(MediaStore.Files.FileColumns.DATA), sel, args,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
        )?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val f = File(c.getString(0) ?: continue)
                if (f.isFile && (showHidden || !f.name.startsWith("."))) out.add(entryOf(f))
            }
        }
        return out
    }

    fun recent(ctx: Context, n: Int = 10): List<Entry> {
        val uri = MediaStore.Files.getContentUri("external")
        val out = ArrayList<Entry>()
        ctx.contentResolver.query(
            uri, arrayOf(MediaStore.Files.FileColumns.DATA),
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (1,2,3) OR ${MediaStore.Files.FileColumns.MIME_TYPE}='application/pdf'", null,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
        )?.use { c ->
            while (c.moveToNext() && out.size < n) {
                val f = File(c.getString(0) ?: continue)
                if (f.isFile && (showHidden || !f.name.startsWith("."))) out.add(entryOf(f))
            }
        }
        return out
    }

    fun search(query: String, limit: Int = 200): List<Entry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<Entry>()
        for (v in vols) {
            v.dir.walkTopDown().onEnter { (showHidden || !it.name.startsWith(".")) && !it.absolutePath.contains("/Android/data") }.forEach {
                if (out.size >= limit) return out
                if (it != v.dir && (showHidden || !it.name.startsWith(".")) && it.name.lowercase().contains(q)) out.add(entryOf(it))
            }
        }
        return out
    }

    fun storage(dir: File = root): Pair<Long, Long> {
        val s = StatFs(dir.absolutePath)
        val total = s.totalBytes
        return (total - s.availableBytes) to total
    }

    fun unique(dir: File, name: String): File {
        var t = File(dir, name)
        if (!t.exists()) return t
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (t.exists()) { t = File(dir, "$base ($i)$ext"); i++ }
        return t
    }

    private fun intoSelf(src: File, dstDir: File) =
        src.isDirectory && (dstDir.absolutePath == src.absolutePath || dstDir.absolutePath.startsWith(src.absolutePath + "/"))

    private fun copyTree(s: File, t: File, onBytes: (Long) -> Unit): Boolean {
        if (s.isDirectory) {
            if (!t.mkdirs() && !t.isDirectory) return false
            var ok = true
            s.listFiles()?.forEach { ok = copyTree(it, File(t, it.name), onBytes) && ok }
            return ok
        }
        s.inputStream().use { i -> t.outputStream().use { o ->
            val buf = ByteArray(256 * 1024)
            while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); onBytes(n.toLong()) }
        } }
        return true
    }

    fun copy(src: File, dstDir: File, onBytes: (Long) -> Unit = {}): Boolean {
        if (intoSelf(src, dstDir)) return false
        val t = unique(dstDir, src.name)
        try { return copyTree(src, t, onBytes) } catch (e: OpCancelled) { t.deleteRecursively(); throw e }
    }

    fun move(src: File, dstDir: File, onBytes: (Long) -> Unit = {}): Boolean {
        if (intoSelf(src, dstDir)) return false
        val t = unique(dstDir, src.name)
        if (src.renameTo(t)) return true
        try { return copyTree(src, t, onBytes) && src.deleteRecursively() } catch (e: OpCancelled) { t.deleteRecursively(); throw e }
    }

    /** Never creates a trash folder on removable drives: those items are deleted permanently. */
    fun trash(src: File, onBytes: (Long) -> Unit = {}): Boolean {
        if (isRemovable(src)) { val s = treeSize(src); return src.deleteRecursively().also { onBytes(s) } }
        trashDir.mkdirs()
        val t = unique(trashDir, src.name)
        val ok = move(src, trashDir, onBytes)
        if (ok) saveRecords(records().apply { add("${t.name}|${src.absolutePath}|${System.currentTimeMillis()}") })
        return ok
    }

    fun rename(src: File, newName: String): Boolean {
        val t = File(src.parentFile, newName)
        val ok = newName.isNotBlank() && !t.exists() && src.renameTo(t)
        if (ok && src.absolutePath in favorites) {
            val old = src.absolutePath
            setFavorite(listOf(old), false); setFavorite(listOf(t.absolutePath), true)
        }
        return ok
    }

    fun zip(files: List<File>, dstDir: File, onBytes: (Long) -> Unit = {}): File {
        val first = files.first()
        val name = if (files.size == 1) first.nameWithoutExtension.ifEmpty { first.name } else "Archive"
        val out = unique(dstDir, "$name.zip")
        try { ZipOutputStream(out.outputStream().buffered()).use { zos ->
            for (f in files) {
                val parent = f.parentFile!!
                f.walkTopDown().forEach { p ->
                    val rel = p.relativeTo(parent).path
                    if (p.isDirectory) zos.putNextEntry(ZipEntry("$rel/")).also { zos.closeEntry() }
                    else {
                        zos.putNextEntry(ZipEntry(rel))
                        p.inputStream().use { i -> val buf = ByteArray(64 * 1024); while (true) { val n = i.read(buf); if (n < 0) break; zos.write(buf, 0, n); onBytes(n.toLong()) } }
                        zos.closeEntry()
                    }
                }
            }
        } } catch (e: OpCancelled) { out.delete(); throw e }
        return out
    }

    fun createdAt(f: File): Long = try {
        Files.readAttributes(f.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java).creationTime().toMillis()
    } catch (_: Exception) { f.lastModified() }

    fun contents(dir: File): Pair<Int, Int> {
        var files = 0; var folders = 0
        dir.walkTopDown().forEach { if (it == dir) Unit else if (it.isDirectory) folders++ else files++ }
        return files to folders
    }

    fun treeSize(f: File): Long = if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun displayPath(f: File): String {
        val v = volumeOf(f)
        return "/${v.label}${f.absolutePath.removePrefix(v.dir.absolutePath)}"
    }
}

fun formatSize(b: Long): String {
    if (b < 1024) return "$b B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = b.toDouble(); var i = -1
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (v >= 100) "%.0f %s".format(v, units[i]) else "%.2f %s".format(v, units[i]).replace(Regex("\\.?0+ "), " ")
}

private val fmtThisYear = java.time.format.DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.ENGLISH)
private val fmtOtherYear = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.ENGLISH)
private val thisYear = java.time.Year.now().value

fun formatDate(ms: Long): String {
    val t = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), java.time.ZoneId.systemDefault())
    return t.format(if (t.year == thisYear) fmtThisYear else fmtOtherYear)
}

fun formatItems(n: Int) = if (n == 1) "1 item" else "$n items"

/** File name as shown in lists: without the extension when "Hide file extensions" is on. */
val Entry.label: String get() = if (!isDir && Fs.hideExt) file.nameWithoutExtension.ifEmpty { name } else name
