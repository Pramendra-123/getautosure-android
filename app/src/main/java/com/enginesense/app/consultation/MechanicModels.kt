package com.enginesense.app.consultation

import org.json.JSONObject

/** One block of working time on one weekday, in India time. [weekday] is ISO: 1 = Monday … 7 = Sunday; times are "HH:mm". */
data class WeeklyHours(val weekday: Int, val start: String, val end: String)

/** Whole days off, India time, both dates inclusive ("yyyy-MM-dd"). */
data class Leave(val id: String, val startDate: String, val endDate: String)

data class MechanicSchedule(val hours: List<WeeklyHours>, val leave: List<Leave>)

/** What the customer's attached scan says, trimmed to what helps before the call. */
data class CarSummary(val brand: String?, val checkEngineOn: Boolean?, val issues: List<String>)

data class MechanicBooking(
    val id: String,
    val bookingReference: String,
    val slotStartEpochMillis: Long,
    val slotEndEpochMillis: Long,
    /** Null when the customer didn't attach a scan. */
    val car: CarSummary?
)

data class MechanicProfile(val expert: Expert, val email: String)

/**
 * Everything Mechanic mode does. The backend works out which mechanic is asking from the Google
 * account signed in to the app, so none of these take an expert id.
 */
interface MechanicRepository {
    suspend fun me(): MechanicProfile
    suspend fun schedule(): MechanicSchedule
    suspend fun setWeeklyHours(hours: List<WeeklyHours>): MechanicSchedule
    suspend fun addLeave(startDate: String, endDate: String): MechanicSchedule
    suspend fun removeLeave(leaveId: String): MechanicSchedule
    suspend fun bookings(): List<MechanicBooking>
    suspend fun mechanicJoinToken(bookingId: String): String
}

/** The backend's "this Google account isn't linked to a mechanic" answer, kept apart so the screen can say what to do about it. */
fun Throwable.isNotAMechanic(): Boolean = this is ConsultationHttpException && code == "not_a_mechanic"

internal object MechanicJson {
    fun schedule(json: JSONObject): MechanicSchedule {
        val hours = json.getJSONArray("hours")
        val leave = json.getJSONArray("leave")
        return MechanicSchedule(
            hours = (0 until hours.length()).map { i ->
                val h = hours.getJSONObject(i)
                WeeklyHours(h.getInt("weekday"), h.getString("start"), h.getString("end"))
            },
            leave = (0 until leave.length()).map { i ->
                val l = leave.getJSONObject(i)
                Leave(l.getString("id"), l.getString("startDate"), l.getString("endDate"))
            }
        )
    }

    fun booking(json: JSONObject): MechanicBooking = MechanicBooking(
        id = json.getString("id"),
        bookingReference = json.getString("bookingReference"),
        slotStartEpochMillis = json.getLong("slotStartEpochMillis"),
        slotEndEpochMillis = json.getLong("slotEndEpochMillis"),
        car = json.optJSONObject("car")?.let { car ->
            val issues = car.optJSONArray("issues")
            CarSummary(
                brand = car.optString("brand").takeIf { car.has("brand") && !car.isNull("brand") && it.isNotBlank() },
                checkEngineOn = if (car.has("checkEngineOn") && !car.isNull("checkEngineOn")) car.getBoolean("checkEngineOn") else null,
                issues = issues?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList()
            )
        }
    )
}
