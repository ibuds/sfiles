package io.github.ibuds.sfiles

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class OpCancelled : java.util.concurrent.CancellationException("Cancelled")

/** A long-running copy / move / compress / delete. [block] reports bytes through its argument and returns an error message or null. */
class OpJob(
    val title: String, val doneText: String, val files: List<File>,
    val block: ((Long) -> Unit) -> String?, val onDone: (String?) -> Unit,
)

/** Live state of one running operation, shown as a card in the app and as its own notification. */
class OpTask(val id: Int, val title: String, val doneText: String, val itemsTotal: Int) {
    var done by mutableLongStateOf(0L)
    var total by mutableLongStateOf(0L)
    var itemsDone by mutableIntStateOf(0)
    val startedAt = SystemClock.elapsedRealtime()
    @Volatile var cancelled = false
    /** Hidden from the in-app card list; it keeps running and keeps its notification. */
    var hidden by mutableStateOf(false)
    val percent: Int get() = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0

    /** Estimated time left in ms, or null while it is too early to tell. */
    fun etaMs(): Long? {
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        if (done <= 0 || total <= 0 || elapsed < 1500) return null
        return (elapsed.toDouble() * (total - done) / done).toLong()
    }
}

fun formatEta(ms: Long): String {
    val s = (ms + 999) / 1000
    return when {
        s < 60 -> "about $s s left"
        s < 3600 -> "about ${(s + 59) / 60} min left"
        else -> "about ${s / 3600} h ${(s % 3600 + 59) / 60} min left"
    }
}

/** "3 of 12 items · 200 MB of 1.4 GB" */
fun OpTask.detail(): String = buildList {
    if (itemsTotal > 1) add("$itemsDone of $itemsTotal items")
    if (total > 0) add("${formatSize(done)} of ${formatSize(total)}")
}.joinToString(" · ")

object Ops {
    /** Every running operation, in start order. Read by the in-app progress cards. */
    val tasks = mutableStateListOf<OpTask>()
    internal val queue = ConcurrentLinkedQueue<Pair<OpTask, OpJob>>()
    private val nextId = AtomicInteger(1)

    /** Starts [job] right away, alongside any others that are running. */
    fun run(ctx: Context, job: OpJob): Boolean {
        val task = OpTask(nextId.getAndIncrement(), job.title, job.doneText, job.files.size)
        tasks.add(task)
        queue.add(task to job)
        ContextCompat.startForegroundService(ctx, Intent(ctx, OpService::class.java))
        return true
    }

    fun cancel(t: OpTask) { t.cancelled = true }
}

/** Foreground service so long operations keep running (with their own progress notifications) when the app is in the background. */
class OpService : Service() {
    private val active = AtomicInteger(0)
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "File operations", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_SVC, "Background work", NotificationManager.IMPORTANCE_MIN))
        if (intent?.action == ACTION_CANCEL) {
            val id = intent.getIntExtra(EXTRA_ID, -1)
            Ops.tasks.firstOrNull { it.id == id }?.cancelled = true
            if (active.get() == 0) stopSelf()
            return START_NOT_STICKY
        }
        // Every startForegroundService call must be answered with startForeground.
        val first = summary()
        if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(SVC_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(SVC_ID, first)
        while (true) { val (task, job) = Ops.queue.poll() ?: break; launch(nm, task, job) }
        if (active.get() == 0) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        return START_NOT_STICKY
    }

    private fun launch(nm: NotificationManager, task: OpTask, job: OpJob) {
        active.incrementAndGet()
        nm.notify(SVC_ID, summary())
        if (Fs.notifications) nm.notify(BASE + task.id, build(task))
        Thread {
            val sizes = try { job.files.map { Fs.treeSize(it) } } catch (_: Exception) { job.files.map { 0L } }
            val prefix = sizes.runningFold(0L) { a, b -> a + b }.drop(1)
            task.total = sizes.sum()
            val done = AtomicLong(0)
            var last = 0L
            val report: (Long) -> Unit = { n ->
                if (task.cancelled) throw OpCancelled()
                val d = done.addAndGet(n)
                val now = SystemClock.elapsedRealtime()
                if (now - last > 250) {
                    last = now
                    task.done = d
                    task.itemsDone = if (d > 0) prefix.count { it <= d } else 0
                    if (Fs.notifications) nm.notify(BASE + task.id, build(task))
                }
            }
            val msg = try { job.block(report) } catch (e: OpCancelled) { "Cancelled" } catch (e: Exception) { e.message ?: "Failed" }
            Ops.tasks.remove(task)
            nm.cancel(BASE + task.id)
            if (Fs.notifications) nm.notify(DONE + task.id, NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle(msg ?: job.doneText)
                .setContentIntent(openApp()).setAutoCancel(true).build())
            Handler(Looper.getMainLooper()).post { job.onDone(msg) }
            if (active.decrementAndGet() == 0) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            else nm.notify(SVC_ID, summary())
        }.start()
    }

    private fun openApp() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun summary(): Notification {
        val n = active.get().coerceAtLeast(1)
        return NotificationCompat.Builder(this, CHANNEL_SVC)
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("SFiles is working")
            .setContentText(if (n == 1) "1 file operation running" else "$n file operations running")
            .setOngoing(true).setContentIntent(openApp()).build()
    }

    private fun build(t: OpTask): Notification {
        val eta = t.etaMs()?.let(::formatEta)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(t.title)
            .setContentText(listOfNotNull(if (t.total > 0) "${t.percent}%" else null, t.detail().ifEmpty { null }).joinToString(" · "))
            .setSubText(eta)
            .setProgress(100, t.percent, t.total == 0L)
            .addAction(0, "Cancel", PendingIntent.getService(
                this, 100 + t.id, Intent(this, OpService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, t.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(openApp()).build()
    }

    private companion object {
        const val CHANNEL = "ops"; const val CHANNEL_SVC = "ops_service"
        const val SVC_ID = 42; const val BASE = 1000; const val DONE = 5000
        const val ACTION_CANCEL = "io.github.ibuds.sfiles.CANCEL"; const val EXTRA_ID = "id"
    }
}
