package nz.afhome.ledger.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import nz.afhome.ledger.MainActivity
import nz.afhome.ledger.R
import nz.afhome.ledger.analysis.Insights
import nz.afhome.ledger.data.fmtDate
import nz.afhome.ledger.ledger
import java.util.concurrent.TimeUnit

/** Writes the encrypted backup to the chosen Google Drive file if anything changed. */
class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.ledger
        if (!app.prefs.autoBackup || !app.backup.hasPin || !app.backup.hasTarget) return Result.success()
        if (!app.prefs.dirty && inputData.getBoolean("force", false).not()) return Result.success()
        return if (app.backup.backupNow().isSuccess) Result.success() else Result.retry()
    }
}

/** Daily check for car paperwork due soon and food about to expire. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.ledger
        val lines = mutableListOf<String>()
        for (v in app.repo.dao.vehicles()) {
            fun check(label: String, day: Long?) {
                val d = day ?: return
                val left = Insights.daysUntil(d)
                if (left in 0..14 && (left % 7 == 0L || left <= 3)) lines += "${v.name}: $label due ${d.fmtDate()} (in $left days)"
                if (left < 0 && left > -30) lines += "${v.name}: $label expired ${d.fmtDate()}"
            }
            check("WoF", v.wofExpiry)
            check("Rego", v.regoExpiry)
            check("Insurance renewal", v.insuranceRenewal)
        }
        app.repo.dao.inventory().filter { it.quantity > 0 && it.expiry != null }.forEach { i ->
            val left = Insights.daysUntil(i.expiry!!)
            if (left in 0..2) lines += "${i.name} expires ${if (left == 0L) "today" else "in $left days"}"
        }
        if (lines.isNotEmpty()) notify(applicationContext, "A&F Home reminders", lines)
        return Result.success()
    }
}

private const val CHANNEL = "reminders"

fun notify(context: Context, title: String, lines: List<String>) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
    val tap = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    val n = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_notify)
        .setContentTitle(title)
        .setContentText(lines.first())
        .setStyle(NotificationCompat.InboxStyle().also { s -> lines.take(6).forEach(s::addLine) })
        .setContentIntent(tap)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(1, n)
}

object Jobs {
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork("backup-daily", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupWorker>(12, TimeUnit.HOURS).build())
        wm.enqueueUniquePeriodicWork("reminders-daily", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build())
    }

    /** Called when the app goes to the background so recent changes reach Drive soon. */
    fun backupSoon(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("backup-soon", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BackupWorker>().setInitialDelay(20, TimeUnit.SECONDS).build())
    }
}
