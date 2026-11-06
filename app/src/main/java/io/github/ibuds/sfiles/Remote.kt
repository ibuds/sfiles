package io.github.ibuds.sfiles

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.share.DiskShare
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.util.EnumSet
import java.util.UUID

enum class Proto(val label: String, val defaultPort: Int) {
    FTP("FTP server", 21), SFTP("SFTP server", 22), SMB("Network drive (SMB)", 445)
}

data class Conn(
    val id: String = UUID.randomUUID().toString(),
    val proto: Proto, val name: String, val host: String, val port: Int,
    val user: String, val pass: String, val anonymous: Boolean,
    /** SMB: share name (required). FTP/SFTP: optional start directory. */
    val path: String = "",
) {
    val url: String get() = "${proto.name.lowercase()}://$host${if (port != proto.defaultPort) ":$port" else ""}"
    fun toJson() = JSONObject().put("id", id).put("proto", proto.name).put("name", name).put("host", host).put("port", port)
        .put("user", user).put("pass", pass).put("anon", anonymous).put("path", path)
    companion object {
        fun fromJson(o: JSONObject) = Conn(
            o.getString("id"), Proto.valueOf(o.getString("proto")), o.getString("name"), o.getString("host"), o.getInt("port"),
            o.optString("user"), o.optString("pass"), o.optBoolean("anon"), o.optString("path"),
        )
    }
}

data class RItem(val name: String, val path: String, val isDir: Boolean, val size: Long, val modified: Long)

fun joinPath(a: String, b: String) = (if (a.endsWith("/")) a else "$a/") .let { if (a.isEmpty()) "/$b" else it + b }
fun parentPath(p: String): String = p.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }

interface RemoteFs : Closeable {
    fun list(path: String): List<RItem>
    fun download(path: String, out: File)
    fun upload(local: File, path: String)
    fun delete(item: RItem)
    fun rename(from: String, to: String)
    fun mkdir(path: String)

    fun downloadTree(item: RItem, destDir: File) {
        val target = Fs.unique(destDir, item.name)
        if (item.isDir) {
            target.mkdirs()
            list(item.path).forEach { downloadTree(it, target) }
        } else download(item.path, target)
    }
}

/** Saved connections; credentials live in EncryptedSharedPreferences. */
object ConnStore {
    @Volatile private var cached: android.content.SharedPreferences? = null
    private fun prefs(ctx: Context): android.content.SharedPreferences = cached ?: synchronized(this) {
        cached ?: EncryptedSharedPreferences.create(
            ctx.applicationContext, "sfiles_connections",
            MasterKey.Builder(ctx.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ).also { cached = it }
    }
    fun all(ctx: Context): List<Conn> = try {
        val arr = JSONArray(prefs(ctx).getString("list", "[]"))
        (0 until arr.length()).map { Conn.fromJson(arr.getJSONObject(it)) }
    } catch (_: Exception) { emptyList() }
    private fun save(ctx: Context, l: List<Conn>) {
        prefs(ctx).edit().putString("list", JSONArray(l.map { it.toJson() }).toString()).apply()
    }
    fun put(ctx: Context, c: Conn) = save(ctx, all(ctx).filter { it.id != c.id } + c)
    fun remove(ctx: Context, id: String) = save(ctx, all(ctx).filter { it.id != id })
    fun get(ctx: Context, id: String) = all(ctx).firstOrNull { it.id == id }
    // SSH host keys, trust on first use
    fun hostKeys(ctx: Context) = ctx.getSharedPreferences("sfiles_hostkeys", Context.MODE_PRIVATE)
}

fun openRemote(ctx: Context, c: Conn): RemoteFs = when (c.proto) {
    Proto.FTP -> FtpFs(c)
    Proto.SFTP -> SftpFs(ctx, c)
    Proto.SMB -> SmbFs(c)
}

private class FtpFs(c: Conn) : RemoteFs {
    private val ftp = FTPClient()
    init {
        ftp.connectTimeout = 15000; ftp.defaultTimeout = 30000
        ftp.connect(c.host, c.port)
        val ok = if (c.anonymous) ftp.login("anonymous", "sfiles@localhost") else ftp.login(c.user, c.pass)
        if (!ok) { runCatching { ftp.disconnect() }; error("Login failed") }
        ftp.enterLocalPassiveMode(); ftp.setFileType(FTP.BINARY_FILE_TYPE); ftp.controlEncoding = "UTF-8"
        if (c.path.isNotBlank()) ftp.changeWorkingDirectory(c.path)
    }
    fun cwd(): String = ftp.printWorkingDirectory() ?: "/"
    override fun list(path: String) = (ftp.listFiles(path) ?: emptyArray())
        .filter { it.name != "." && it.name != ".." }
        .map { RItem(it.name, joinPath(path, it.name), it.isDirectory, it.size, it.timestamp?.timeInMillis ?: 0L) }
    override fun download(path: String, out: File) {
        out.outputStream().use { if (!ftp.retrieveFile(path, it)) error("Download failed") }
    }
    override fun upload(local: File, path: String) {
        local.inputStream().use { if (!ftp.storeFile(path, it)) error("Upload failed") }
    }
    override fun delete(item: RItem) {
        if (item.isDir) { list(item.path).forEach { delete(it) }; ftp.removeDirectory(item.path) } else ftp.deleteFile(item.path)
    }
    override fun rename(from: String, to: String) { if (!ftp.rename(from, to)) error("Rename failed") }
    override fun mkdir(path: String) { if (!ftp.makeDirectory(path)) error("Couldn't create folder") }
    override fun close() { runCatching { ftp.logout() }; runCatching { ftp.disconnect() } }
}

private class SftpFs(ctx: Context, c: Conn) : RemoteFs {
    private val jsch = JSch()
    private val session: Session
    private val ch: ChannelSftp
    init {
        val keys = ConnStore.hostKeys(ctx)
        jsch.hostKeyRepository = object : HostKeyRepository {
            override fun check(host: String, key: ByteArray): Int {
                val fp = HostKey(host, key).getFingerPrint(jsch)
                val known = keys.getString(host, null)
                return when {
                    known == null -> { keys.edit().putString(host, fp).apply(); HostKeyRepository.OK }
                    known == fp -> HostKeyRepository.OK
                    else -> HostKeyRepository.CHANGED
                }
            }
            override fun add(hostkey: HostKey, ui: UserInfo?) {}
            override fun remove(host: String?, type: String?) {}
            override fun remove(host: String?, type: String?, key: ByteArray?) {}
            override fun getKnownHostsRepositoryID() = "sfiles"
            override fun getHostKey(): Array<HostKey> = emptyArray()
            override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
        }
        session = jsch.getSession(c.user, c.host, c.port)
        session.setPassword(c.pass)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.connect(15000)
        ch = session.openChannel("sftp") as ChannelSftp
        ch.connect(15000)
        if (c.path.isNotBlank()) runCatching { ch.cd(c.path) }
    }
    fun cwd(): String = ch.pwd()
    @Suppress("UNCHECKED_CAST")
    override fun list(path: String) = (ch.ls(path) as java.util.Vector<ChannelSftp.LsEntry>)
        .filter { it.filename != "." && it.filename != ".." }
        .map { RItem(it.filename, joinPath(path, it.filename), it.attrs.isDir, it.attrs.size, it.attrs.mTime * 1000L) }
    override fun download(path: String, out: File) { ch.get(path, out.absolutePath) }
    override fun upload(local: File, path: String) { ch.put(local.absolutePath, path) }
    override fun delete(item: RItem) {
        if (item.isDir) { list(item.path).forEach { delete(it) }; ch.rmdir(item.path) } else ch.rm(item.path)
    }
    override fun rename(from: String, to: String) { ch.rename(from, to) }
    override fun mkdir(path: String) { ch.mkdir(path) }
    override fun close() { runCatching { ch.disconnect() }; runCatching { session.disconnect() } }
}

private class SmbFs(c: Conn) : RemoteFs {
    private val client = SMBClient()
    private val conn: Connection = client.connect(c.host, c.port)
    private val share: DiskShare
    init {
        val auth = if (c.anonymous) AuthenticationContext.guest() else {
            val domain = if (c.user.contains('\\')) c.user.substringBefore('\\') else null
            AuthenticationContext(c.user.substringAfter('\\'), c.pass.toCharArray(), domain)
        }
        share = conn.authenticate(auth).connectShare(c.path.trim('/').ifEmpty { error("Share name is required") }) as DiskShare
    }
    private fun p(path: String) = path.trim('/').replace('/', '\\')
    override fun list(path: String) = share.list(p(path))
        .filter { it.fileName != "." && it.fileName != ".." }
        .map {
            RItem(it.fileName, joinPath(path, it.fileName),
                EnumWithValue.EnumUtils.isSet(it.fileAttributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY),
                it.endOfFile, it.lastWriteTime.toEpochMillis())
        }
    override fun download(path: String, out: File) {
        share.openFile(p(path), EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null).use { f ->
            f.inputStream.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        }
    }
    override fun upload(local: File, path: String) {
        share.openFile(p(path), EnumSet.of(AccessMask.GENERIC_WRITE), EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
            SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OVERWRITE_IF, null).use { f ->
            f.outputStream.use { o -> local.inputStream().use { it.copyTo(o) } }
        }
    }
    override fun delete(item: RItem) { if (item.isDir) share.rmdir(p(item.path), true) else share.rm(p(item.path)) }
    override fun rename(from: String, to: String) {
        share.open(p(from), EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_WRITE), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
            .use { it.rename(p(to)) }
    }
    override fun mkdir(path: String) { share.mkdir(p(path)) }
    override fun close() { runCatching { conn.close() }; runCatching { client.close() } }
}

/** Start directory shown after connecting. */
fun startPath(fs: RemoteFs, c: Conn): String = when (fs) {
    is SmbFs -> "/"
    else -> when {
        fs is FtpFs -> fs.cwd()
        fs is SftpFs -> fs.cwd()
        else -> "/"
    }
}
