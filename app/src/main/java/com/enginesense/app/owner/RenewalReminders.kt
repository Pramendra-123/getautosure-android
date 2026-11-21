package com.enginesense.app.owner

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.enginesense.app.MainActivity
import com.enginesense.app.R
import com.enginesense.app.storage.GarageStore
import com.enginesense.app.storage.OwnerDataStore
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Free renewal reminders: once a day, on the phone, check every car's saved dates (the RTO record
 * from when it was added, or a date the owner set) and post a notification 30, 7 and 1 day before
 * and on the day. No server and no new RTO lookup — those cost money per call.
 */
object RenewalReminders {
    private const val CHANNEL_ID = "renewal_reminders"
    private const val WORK_NAME = "renewal_reminders_daily"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<RenewalReminderWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    internal fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Renewal reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Insurance, PUC, road tax, fitness and licence expiry"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    internal fun checkNow(context: Context, today: LocalDate = LocalDate.now()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val prefs = context.getSharedPreferences("renewal_reminders", Context.MODE_PRIVATE)
        val owner = OwnerDataStore(context)
        GarageStore(context).load().cars.forEachIndexed { carIndex, car ->
            val data = owner.load(car.id)
            if (!data.remindersOn) return@forEachIndexed
            OwnerInsights.remindersDue(OwnerInsights.renewals(car.profile.rc, data, today)).forEach { item ->
                // One reminder per renewal per day, however often the worker runs.
                val key = "${car.id}_${item.kind}_$today"
                if (prefs.getBoolean(key, false)) return@forEach
                post(context, carIndex * 10 + item.kind.ordinal, car.title, item)
                prefs.edit().putBoolean(key, true).apply()
            }
        }
    }

    private fun post(context: Context, id: Int, carTitle: String, item: RenewalItem) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${item.kind.label} · $carTitle")
            .setContentText(item.display)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(7_300 + id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and here — a missed reminder, not a crash.
        }
    }
}

class RenewalReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        RenewalReminders.checkNow(applicationContext)
        return Result.success()
    }
}
