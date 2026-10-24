package com.enginesense.app.consultation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Fires [ConsultationNotifications.showExpiryWarning] once the delay set by [ConsultationNotifications.scheduleExpiryWarning] elapses — may run in a fresh process with no [ConsultationViewModel] ever created, so it only touches [ConsultationNotifications], never the ViewModel. */
class ReservationExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val expertName = inputData.getString(ConsultationNotifications.KEY_EXPERT_NAME) ?: return Result.failure()
        val bookingReference = inputData.getString(ConsultationNotifications.KEY_BOOKING_REFERENCE) ?: return Result.failure()
        ConsultationNotifications.showExpiryWarning(applicationContext, expertName, bookingReference)
        return Result.success()
    }
}
