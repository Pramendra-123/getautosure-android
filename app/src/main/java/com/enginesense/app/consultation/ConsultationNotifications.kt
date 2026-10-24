package com.enginesense.app.consultation

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
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.enginesense.app.MainActivity
import com.enginesense.app.R
import java.util.concurrent.TimeUnit

/**
 * Local-only alerts for the consultation-booking lifecycle (reservation about to expire,
 * cancellation confirmed) — no server push/FCM here, that's a later phase (richer reminders tied
 * to real payment/video need a server trigger; these two don't, they're driven entirely by state
 * this device already has). Every posting function is a no-op without POST_NOTIFICATIONS (API
 * 33+) rather than crashing — a missed courtesy alert, not a broken booking.
 */
object ConsultationNotifications {
    private const val CHANNEL_ID = "consultation_updates"
    private const val CANCEL_NOTIFICATION_ID_BASE = 9_200
    private const val EXPIRY_NOTIFICATION_ID_BASE = 9_100
    private const val EXPIRY_WORK_NAME_PREFIX = "consultation_expiry_warning_"

    // Mirrors the Edge Function's RESERVATION_MINUTES (supabase/functions/consultations/
    // index.ts) — only this notification's timing needs it, so it isn't worth promoting to
    // ConsultationPricing alongside the values every screen reads.
    private const val RESERVATION_WINDOW_MINUTES = 10L
    private const val WARNING_LEAD_MINUTES = 2L

    internal const val KEY_EXPERT_NAME = "expertName"
    internal const val KEY_BOOKING_REFERENCE = "bookingReference"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Consultation updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reservation holds about to expire, and booking cancellations"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Schedules [ReservationExpiryWorker] to fire a warning a couple of minutes before the slot's 10-minute hold lapses — survives the app being backgrounded, unlike a plain coroutine delay tied to this ViewModel's own lifecycle. */
    fun scheduleExpiryWarning(context: Context, bookingId: String, expertName: String, bookingReference: String) {
        val delayMinutes = (RESERVATION_WINDOW_MINUTES - WARNING_LEAD_MINUTES).coerceAtLeast(0)
        val data = Data.Builder()
            .putString(KEY_EXPERT_NAME, expertName)
            .putString(KEY_BOOKING_REFERENCE, bookingReference)
            .build()
        val request = OneTimeWorkRequestBuilder<ReservationExpiryWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            EXPIRY_WORK_NAME_PREFIX + bookingId,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Called once a reservation is confirmed or explicitly cancelled — the warning no longer applies either way. */
    fun cancelExpiryWarning(context: Context, bookingId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(EXPIRY_WORK_NAME_PREFIX + bookingId)
    }

    /** Called only by [ReservationExpiryWorker] once its scheduled delay elapses. */
    internal fun showExpiryWarning(context: Context, expertName: String, bookingReference: String) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your reserved slot is expiring soon")
            .setContentText("Complete payment for your consultation with $expertName ($bookingReference) or the slot will be released.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(EXPIRY_NOTIFICATION_ID_BASE + bookingReference.hashCode(), notification)
    }

    fun showCancellationConfirmation(context: Context, expertName: String, bookingReference: String) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Booking cancelled")
            .setContentText("Your consultation with $expertName ($bookingReference) has been cancelled.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(CANCEL_NOTIFICATION_ID_BASE + bookingReference.hashCode(), notification)
    }

    private fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
}
