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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class OpCancelled : java.util.concurrent.CancellationException("Cancelled")

data class OpProgress(val title: String, val done: Long, val total: Long)

/** A long-running copy / move / compress. [block] reports bytes through its argument and returns an error message or null. */
class OpJob(
    val title: String, val doneText: String, val files: List<File>,
    val block: ((Long) -> Unit) -> String?, val onDone: (String?) -> Unit,
)

object Ops {
    /** Progress of the running operation, or null. Read by the in-app progress card. */
    var progress by mutableStateOf<OpProgress?>(null)
    @Volatile internal var pending: OpJob? = null
    @Volatile var running = false
    @Volatile var cancelled = false
    fun cancel() { cancelled = true }

    fun run(ctx: Context, job: OpJob): Boolean {
        if (running) return false
        running = true
        cancelled = false
        pending = job
        progress = OpProgress(job.title, 0, 0)
        ContextCompat.startForegroundService(ctx, Intent(ctx, OpService::class.java))
        return true
    }
}

/** Foreground service so a long copy keeps running (and shows a progress notification) when the app is in the background. */
class OpService : Service() {
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) { Ops.cancel(); return START_NOT_STICKY }
        val job = Ops.pending ?: run { Ops.running = false; stopSelf(); return START_NOT_STICKY }
        Ops.pending = null
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "File operations", NotificationManager.IMPORTANCE_LOW))
        val first = build(job.title, null, 0, 0, true)
        if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(ID, first)

        Thread {
            val total = try { job.files.sumOf { Fs.treeSize(it) } } catch (_: Exception) { 0L }
            val done = AtomicLong(0)
            var last = 0L
            Ops.progress = OpProgress(job.title, 0, total)
            val report: (Long) -> Unit = { n ->
                if (Ops.cancelled) throw OpCancelled()
                val d = done.addAndGet(n)
                val now = SystemClock.elapsedRealtime()
                if (now - last > 250) {
                    last = now
                    Ops.progress = OpProgress(job.title, d, total)
                    nm.notify(ID, build(job.title, "${formatSize(d)} of ${formatSize(total)}", d, total, false))
                }
            }
            val msg = try { job.block(report) } catch (e: OpCancelled) { "Cancelled" } catch (e: Exception) { e.message ?: "Failed" }
            Ops.progress = null
            Ops.running = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            nm.notify(ID + 1, NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle(msg ?: job.doneText)
                .setContentIntent(openApp()).setAutoCancel(true).build())
            Handler(Looper.getMainLooper()).post { job.onDone(msg) }
            stopSelf()
        }.start()
        return START_NOT_STICKY
    }

    private fun openApp() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun build(title: String, text: String?, done: Long, total: Long, indeterminate: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(title).setContentText(text)
            .setProgress(100, if (total > 0) (done * 100 / total).toInt() else 0, indeterminate || total == 0L)
            .addAction(0, "Cancel", PendingIntent.getService(
                this, 1, Intent(this, OpService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(openApp()).build()

    private companion object { const val CHANNEL = "ops"; const val ID = 42; const val ACTION_CANCEL = "io.github.ibuds.sfiles.CANCEL" }
}
