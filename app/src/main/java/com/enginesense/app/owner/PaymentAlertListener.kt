package com.enginesense.app.owner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.enginesense.app.MainActivity
import com.enginesense.app.R
import com.enginesense.app.storage.GarageStore
import com.enginesense.app.storage.OwnerDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.util.Locale

/**
 * Car wallet auto-capture. With the owner's OK (phone Settings → Notification access), this sees
 * the alerts SMS and UPI apps post, and when one is a fuel, toll or parking payment it adds it to
 * the picked car. The alert text never leaves the phone and nothing else is kept — chats, OTPs
 * and other spending are read past and dropped. Google Play's SMS permission isn't used.
 */
class PaymentAlertListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || !fromPaymentApp(sbn.packageName)) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        alertTexts(n).forEach { capture(applicationContext, it) }
    }

    private fun alertTexts(n: Notification): List<String> {
        val messages = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)?.messages?.mapNotNull { it.text?.toString() }
        if (!messages.isNullOrEmpty()) return messages.takeLast(3)
        val e = n.extras
        val text = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        return listOfNotNull(text)
    }

    companion object {
        private const val CHANNEL_ID = "wallet_captures"
        private const val MAX_KEYS = 300

        /** SMS apps (bank, UPI and FASTag SMS arrive here) and the big UPI apps. Nothing else is looked at. */
        private val PAYMENT_APPS = setOf(
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms", "com.android.messaging",
            "com.oneplus.mms", "com.truecaller", "com.google.android.apps.nbu.paisa.user", "com.phonepe.app",
            "net.one97.paytm", "in.org.npci.upiapp", "com.dreamplug.androidapp", "in.amazon.mShop.android.shopping"
        )

        private fun fromPaymentApp(pkg: String) = pkg in PAYMENT_APPS || pkg.endsWith(".mms")

        private val _captures = MutableStateFlow(0L)
        /** Bumps each time a payment is added, so an open wallet reloads. */
        val captures: StateFlow<Long> = _captures.asStateFlow()

        fun isEnabled(context: Context): Boolean = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        /** Phone Settings, as close to GetAutoSure's switch as the Android version allows. */
        fun settingsIntent(context: Context): Intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, PaymentAlertListener::class.java).flattenToString())
            } else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

        private fun keyOf(text: String) = text.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim().hashCode().toString(16)

        /** Adds the payment in [text] to the picked car if it's a car payment we haven't added yet. Returns true when added. */
        @Synchronized
        fun capture(context: Context, text: String, today: LocalDate = LocalDate.now()): Boolean {
            val payment = PaymentSmsParser.parseAlert(text, today) ?: return false
            val garage = GarageStore(context).load()
            val car = garage.active ?: return false
            val store = OwnerDataStore(context)
            val data = store.load(car.id)
            val key = keyOf(text)
            if (key in data.capturedKeys) return false
            val id = System.currentTimeMillis()
            val date = (payment.date ?: today).toString()
            val note = payment.merchant ?: ""
            val updated = when (payment.kind) {
                PaymentKind.FUEL -> data.copy(fuel = data.fuel + FuelEntry(id, date, null, payment.amountInr, null, fullTank = false, source = EntrySource.ALERT))
                PaymentKind.TOLL -> data.copy(expenses = data.expenses + ExpenseEntry(id, date, ExpenseCategory.TOLL, payment.amountInr, note, EntrySource.ALERT))
                else -> data.copy(expenses = data.expenses + ExpenseEntry(id, date, ExpenseCategory.PARKING, payment.amountInr, note, EntrySource.ALERT))
            }.copy(capturedKeys = (data.capturedKeys + key).takeLast(MAX_KEYS))
            store.save(car.id, updated)
            _captures.value = id
            tell(context, "${OwnerInsights.inr(payment.amountInr)}${payment.merchant?.let { " at $it" } ?: ""} · ${payment.kind.label}", "Added to ${car.title}. Tap to change.")
            return true
        }

        private fun tell(context: Context, title: String, text: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Car wallet", NotificationManager.IMPORTANCE_LOW).apply { description = "When a car payment is added automatically" }
                )
            }
            val open = PendingIntent.getActivity(
                context, 1, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_ROUTE, "wallet").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
                .setContentIntent(open).setAutoCancel(true).build()
            try {
                NotificationManagerCompat.from(context).notify(7_500 + (System.currentTimeMillis() % 1000).toInt(), n)
            } catch (e: SecurityException) {
                // No notification permission: the payment is still added, just quietly.
            }
        }
    }
}
