package com.enginesense.app.owner

import com.enginesense.app.vehicle.FairPriceEstimate
import com.enginesense.app.vehicle.FairPriceEstimator
import com.enginesense.app.vehicle.ReadinessStatus
import com.enginesense.app.vehicle.pricing.ExShowroomPrices
import com.enginesense.app.vehicle.pricing.Variant
import com.enginesense.app.vehicle.pricing.priceFor
import com.enginesense.app.vehiclelookup.RcLookupResult
import com.enginesense.app.vehiclelookup.RtoRecord
import com.enginesense.app.vehiclelookup.ValidityStatus
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class RenewalItem(
    val kind: RenewalKind,
    val date: LocalDate?,
    val display: String,
    val status: ValidityStatus,
    val daysLeft: Long?,
    /** True when the date is the RTO record's, false when the owner set it. */
    val fromRto: Boolean
)

data class NextService(val dueKm: Int?, val kmLeft: Int?, val dueDate: LocalDate, val daysLeft: Long) {
    val overdue: Boolean get() = daysLeft < 0 || (kmLeft != null && kmLeft < 0)
}

data class MonthSpend(val total: Int, val fuel: Int, val service: Int, val other: Int)

enum class Level { GOOD, WATCH, ATTENTION, UNKNOWN }

data class PucVerdict(val level: Level, val headline: String, val detail: String)

data class BatteryHealth(val level: Level, val headline: String, val lines: List<String>)

/**
 * The owner dashboard's numbers, worked out from what the app already has — the saved RTO
 * record, the owner's own entries, the last scan and the trip recorder. Pure, so it's unit-tested.
 * Nothing here calls the RTO again: those lookups cost money, so renewals use the saved dates.
 */
object OwnerInsights {

    private val DISPLAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    /** Within this many days the countdown is shown instead of the plain "valid till" date. */
    const val COUNTDOWN_DAYS = 60L

    /** Reminders go out this many days before a renewal (and on the day). */
    val REMINDER_DAYS = setOf(30L, 7L, 1L, 0L)

    private fun rtoRaw(rc: RcLookupResult?, kind: RenewalKind): String? = when (kind) {
        RenewalKind.INSURANCE -> rc?.insuranceUpto
        RenewalKind.PUC -> rc?.puccUpto
        RenewalKind.ROAD_TAX -> rc?.taxUpto
        RenewalKind.FITNESS -> rc?.fitnessUpto
        RenewalKind.LICENCE -> null
    }

    fun renewals(rc: RcLookupResult?, data: OwnerCarData, today: LocalDate): List<RenewalItem> = RenewalKind.entries.map { kind ->
        val own = data.renewalDates[kind]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val raw = rtoRaw(rc, kind)
        val status = when {
            own != null -> when {
                own.isBefore(today) -> ValidityStatus.EXPIRED
                ChronoUnit.DAYS.between(today, own) <= RtoRecord.EXPIRY_WARNING_DAYS -> ValidityStatus.EXPIRES_SOON
                else -> ValidityStatus.VALID
            }
            else -> RtoRecord.status(raw, today)
        }
        val date = own ?: RtoRecord.parseDate(raw)
        val daysLeft = date?.let { ChronoUnit.DAYS.between(today, it) }
        val display = when {
            status == ValidityStatus.LIFETIME -> "Lifetime (paid once)"
            date == null -> if (raw != null) raw else "Add the expiry date"
            daysLeft!! < 0 -> "Expired on ${date.format(DISPLAY)}"
            daysLeft == 0L -> "Expires today"
            daysLeft == 1L -> "Expires tomorrow"
            daysLeft <= COUNTDOWN_DAYS -> "Expires in $daysLeft days · ${date.format(SHORT)}"
            else -> "Valid till ${date.format(DISPLAY)}"
        }
        RenewalItem(kind, date, display, status, daysLeft, fromRto = own == null && raw != null)
    }

    /** Renewals to remind about today — exactly 30, 7 or 1 day before, or on the day. */
    fun remindersDue(items: List<RenewalItem>): List<RenewalItem> = items.filter { it.daysLeft != null && it.daysLeft in REMINDER_DAYS }

    /** The highest odometer reading we know of, from any source. */
    fun currentOdometer(data: OwnerCarData, vararg others: Int?): Int? =
        (data.services.mapNotNull { it.odometerKm } + data.fuel.mapNotNull { it.odometerKm } + others.filterNotNull()).maxOrNull()

    fun nextService(data: OwnerCarData, currentOdometerKm: Int?, today: LocalDate): NextService? {
        val last = data.services.maxByOrNull { it.date } ?: return null
        val lastDate = runCatching { LocalDate.parse(last.date) }.getOrNull() ?: return null
        val dueDate = lastDate.plusMonths(data.serviceIntervalMonths.toLong())
        val dueKm = last.odometerKm?.let { it + data.serviceIntervalKm }
        val kmLeft = if (dueKm != null && currentOdometerKm != null) dueKm - currentOdometerKm else null
        return NextService(dueKm, kmLeft, dueDate, ChronoUnit.DAYS.between(today, dueDate))
    }

    /**
     * Real mileage from fill-ups, worked out the full-tank-to-full-tank way: the km between the
     * first and last full tank, over the litres put in after the first one. Null until there are
     * two full tanks with odometer readings and every fill in between has its litres.
     */
    fun fuelMileage(fuel: List<FuelEntry>): Double? {
        val withKm = fuel.filter { it.odometerKm != null }.sortedBy { it.odometerKm }
        val first = withKm.indexOfFirst { it.fullTank }
        val last = withKm.indexOfLast { it.fullTank }
        if (first < 0 || last <= first) return null
        val between = withKm.subList(first + 1, last + 1)
        if (between.any { it.litres == null }) return null
        val litres = between.sumOf { it.litres!! }
        val km = withKm[last].odometerKm!! - withKm[first].odometerKm!!
        if (litres <= 0.0 || km <= 0) return null
        return km / litres
    }

    fun monthSpend(data: OwnerCarData, month: YearMonth): MonthSpend {
        fun inMonth(date: String) = runCatching { YearMonth.from(LocalDate.parse(date)) == month }.getOrDefault(false)
        val fuel = data.fuel.filter { inMonth(it.date) }.sumOf { it.amountInr }
        val serviceLog = data.services.filter { inMonth(it.date) }.sumOf { it.costInr ?: 0 }
        val expenses = data.expenses.filter { inMonth(it.date) }
        val serviceExpenses = expenses.filter { it.category == ExpenseCategory.SERVICE || it.category == ExpenseCategory.PARTS }.sumOf { it.amountInr }
        val other = expenses.sumOf { it.amountInr } - serviceExpenses
        val service = serviceLog + serviceExpenses
        return MonthSpend(fuel + service + other, fuel, service, other)
    }

    /**
     * "Will you pass PUC?" A PUC centre tests the exhaust gas, not the OBD port, so this is a
     * likelihood: cars with the check-engine light off and every emission self-test finished
     * usually pass. An unfinished self-test usually means the battery was disconnected or codes
     * were cleared recently, and the car needs a few days of normal driving.
     */
    fun pucVerdict(readiness: ReadinessStatus?, storedCodes: Int): PucVerdict {
        if (readiness == null) return PucVerdict(Level.UNKNOWN, "Scan to find out", "Plug in the scanner and scan with the engine on.")
        val supported = readiness.monitors.filter { it.supported }
        val notReady = supported.count { !it.ready }
        return when {
            readiness.milOn -> PucVerdict(Level.ATTENTION, "Likely to fail", "The check-engine light is on. Get it fixed before the PUC test.")
            notReady > 0 -> PucVerdict(
                Level.WATCH, "Not ready yet",
                "$notReady of ${supported.size} emission self-tests haven't finished. This usually happens after the battery was disconnected or codes were cleared. Drive normally for 2–3 days, then scan again."
            )
            storedCodes > 0 -> PucVerdict(Level.WATCH, "Likely pass", "All ${supported.size} emission self-tests are ready, but $storedCodes fault code${if (storedCodes == 1) " is" else "s are"} stored. Worth a look first.")
            else -> PucVerdict(Level.GOOD, "Likely pass", "All ${supported.size} emission self-tests are ready and the check-engine light is off.")
        }
    }

    /**
     * Battery and charging from every reading we have: the scan's module voltage (engine running),
     * and from the trip recorder the resting voltage (parked), the lowest voltage while starting
     * the engine (the start test), and the charging voltage while driving.
     */
    fun batteryHealth(scanVolts: Double?, restingVolts: Double?, crankingMinVolts: Double?, chargingVolts: Double?): BatteryHealth {
        val findings = mutableListOf<Pair<Level, String>>()
        crankingMinVolts?.let { v ->
            findings += when {
                v < 9.6 -> Level.ATTENTION to "Start test: dropped to %.1f V while starting. The battery is weak — get it load-tested.".format(v)
                v < 10.0 -> Level.WATCH to "Start test: dropped to %.1f V while starting. A little low; keep an eye on it.".format(v)
                else -> Level.GOOD to "Start test: lowest %.1f V while starting. Good.".format(v)
            }
        }
        restingVolts?.let { v ->
            findings += when {
                v < 11.9 -> Level.ATTENTION to "Parked: %.1f V. The battery is low on charge.".format(v)
                v < 12.2 -> Level.WATCH to "Parked: %.1f V. A bit low — short trips may not be recharging it.".format(v)
                else -> Level.GOOD to "Parked: %.1f V. Fully charged.".format(v)
            }
        }
        (chargingVolts ?: scanVolts)?.let { v ->
            findings += when {
                v > 15.0 -> Level.ATTENTION to "Charging: %.1f V. Too high — the alternator may be overcharging.".format(v)
                v < 13.0 -> Level.ATTENTION to "Charging: %.1f V. Too low — the alternator may not be charging.".format(v)
                else -> Level.GOOD to "Charging: %.1f V. Normal.".format(v)
            }
        }
        if (findings.isEmpty()) return BatteryHealth(Level.UNKNOWN, "Scan to check", listOf("Scan your car, or turn on trip recording to run the start test."))
        val worst = findings.maxOf { it.first.ordinal }.let { Level.entries[it] }
        val headline = when (worst) {
            Level.GOOD -> "Good"
            Level.WATCH -> "Keep an eye on it"
            Level.ATTENTION -> "Needs a check"
            Level.UNKNOWN -> "Scan to check"
        }
        return BatteryHealth(worst, headline, findings.map { it.second })
    }

    /**
     * Why mileage is below the company figure — only reasons the trips or the last scan actually
     * show, worst first. [totalFuelTrimPercent] is short + long term trim from the scan: a big
     * positive number means the engine is adding fuel (running rich or a sensor problem).
     */
    fun mileageReasons(
        trips: List<com.enginesense.app.drivelab.DriveTrip>,
        totalFuelTrimPercent: Double?
    ): List<String> {
        val reasons = mutableListOf<String>()
        val driveSeconds = trips.sumOf { it.durationSeconds }.coerceAtLeast(1)
        if (trips.isNotEmpty()) {
            val idleShare = trips.sumOf { it.idleSeconds }.toDouble() / driveSeconds
            if (idleShare > 0.2) reasons += "Idling: the engine was idling ${(idleShare * 100).toInt()}% of the time. Switch off for long stops."
            val harsh = trips.sumOf { it.harshAccelerations }
            val highRpmMin = trips.sumOf { it.highRpmSeconds } / 60
            if (harsh >= 3 || highRpmMin >= 3) reasons += "Hard acceleration: $harsh harsh starts and $highRpmMin min above 4,000 rpm. Gentle starts save fuel."
            val warmUps = trips.mapNotNull { t -> t.warmUpSeconds?.let { it.toDouble() / t.durationSeconds.coerceAtLeast(1) } }
            if (warmUps.isNotEmpty() && warmUps.average() > 0.3) reasons += "Short trips: much of each trip is on a cold engine, which uses more fuel."
            val moving = trips.mapNotNull { it.avgMovingSpeedKmh }
            if (moving.isNotEmpty() && moving.average() < 20) reasons += "Slow traffic: average moving speed %.0f km/h. Stop-and-go driving lowers mileage.".format(moving.average())
            val fastMin = trips.sumOf { it.overSpeedSeconds } / 60
            if (fastMin >= 10) reasons += "High speed: $fastMin min above 100 km/h. Mileage drops quickly above 90 km/h."
        }
        totalFuelTrimPercent?.let { if (it > 10) reasons += "Engine running rich: it's adding %.0f%% extra fuel. Often a dirty air filter, an O2 sensor or a vacuum leak.".format(it) }
        return reasons
    }

    /** Tips for causes the OBD port can't see — shown under the reasons, never as findings. */
    val MILEAGE_TIPS = listOf("Check tyre pressure every two weeks.", "AC on full can cost 10–15% mileage in city driving.")

    /** Tentative value from the ex-showroom table (mid variant), age and km — null when the model isn't in the table. */
    fun carValue(make: String?, model: String?, year: Int?, odometerKm: Int?, today: LocalDate, fuelType: String? = null): FairPriceEstimate? {
        val newPrice = ExShowroomPrices.lookup(make, model)?.priceFor(Variant.MID) ?: return null
        val y = year ?: return null
        val age = (today.year - y).toDouble() + (today.monthValue - 6) / 12.0
        return FairPriceEstimator.estimate(newPrice, age.coerceAtLeast(0.0), odometerKm)
    }

    fun inr(amount: Int): String = "₹" + NumberFormat.getIntegerInstance(Locale("en", "IN")).format(amount)

    /** "₹9.1–10.7 L" for amounts in lakhs. */
    fun lakhRange(low: Double, high: Double): String = "₹%.1f–%.1f L".format(Locale.US, low / 100_000, high / 100_000)
}
