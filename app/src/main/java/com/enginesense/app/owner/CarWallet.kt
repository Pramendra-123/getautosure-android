package com.enginesense.app.owner

import com.enginesense.app.vehicle.FairPriceEstimator
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** The car's cost groups in the wallet. Fuel, insurance and service can be estimated; the rest only comes from real payments. */
enum class CostKind(val label: String) { FUEL("Fuel"), INSURANCE("Insurance"), SERVICE("Service and repairs"), OTHER("Toll, parking, PUC, wash") }

/**
 * One cost group for a year. [confirmed] means it comes from the owner's real payments; otherwise
 * it's an estimate, worked out as [basis] says.
 */
data class CostLine(val kind: CostKind, val yearlyInr: Int, val confirmed: Boolean, val basis: String)

enum class KmSource { OWNER, TRIPS, ODOMETER, GUESS }

data class KmPerMonth(val km: Int, val source: KmSource)

/** What the wallet knows about the car, gathered from the profile, the saved RTO record, the scanner and the trips. */
data class WalletCar(
    /** Ex-showroom price of the model when new (mid variant). Null when the model isn't in the price table. */
    val newPrice: Double?,
    /** From the RTO record, else mid-year of the model year. */
    val registered: LocalDate?,
    val odometerKm: Int?,
    val engineCc: Int?,
    val fuelType: String?,
    /** The company's mileage figure. */
    val claimedKmpl: Double?,
    /** Mileage the scanner measured, corrected by fill-ups when there are two full tanks. */
    val measuredKmpl: Double?,
    /** Km a month from recorded trips, when enough days were recorded. */
    val tripKmPerMonth: Double?,
    val healthScore: Int?
)

data class WalletSummary(
    val lines: List<CostLine>,
    val km: KmPerMonth,
    val kmpl: Double,
    val fuelPricePerLitre: Double,
    /** Money spent on this car since 1 January, from real entries only. */
    val spentThisYear: Int,
    /** How much the car's value drops over the next year — not money paid, but a cost of owning it. */
    val valueLostThisYear: Int?,
    val ageYears: Double?
) {
    val yearlyTotal: Int get() = lines.sumOf { it.yearlyInr }
    val confirmedCount: Int get() = lines.count { it.kind != CostKind.OTHER && it.confirmed }
    val confirmableCount: Int get() = lines.count { it.kind != CostKind.OTHER }
    val allConfirmed: Boolean get() = confirmedCount == confirmableCount
    val costPerKm: Double? get() = (km.km * 12).takeIf { it > 0 }?.let { yearlyTotal.toDouble() / it }
    fun line(kind: CostKind): CostLine = lines.first { it.kind == kind }
}

/**
 * My Car Wallet's numbers. On day one there are no payments, so every group is an estimate from
 * what the app already knows — km a month × mileage × fuel price for fuel, the car's value for
 * insurance, its age and price for service. As real payments come in (typed, shared, spotted from
 * a payment alert or a fill-up), each group switches to the real figure. Pure, so it's unit-tested.
 */
object CarWallet {

    /** Real-world mileage is usually below the company figure. */
    const val REAL_WORLD_SHARE = 0.85
    /** Below this many days of payments a group isn't annualised (one fill-up isn't a year of fuel). */
    private const val MIN_SPAN_DAYS = 28L
    private const val GST = 1.18

    fun defaultFuelPrice(fuelType: String?): Double = when (fuelType?.lowercase()) {
        "diesel" -> 90.0
        "cng" -> 80.0
        else -> 103.0
    }

    private fun defaultKmpl(fuelType: String?): Double = when (fuelType?.lowercase()) {
        "diesel" -> 17.0
        "cng" -> 20.0
        else -> 14.0
    }

    /** Third-party premium is fixed by IRDAI by engine size (private cars, yearly). */
    fun thirdPartyPremium(engineCc: Int?): Int = when {
        engineCc == null -> 3_416
        engineCc <= 1000 -> 2_094
        engineCc <= 1500 -> 3_416
        else -> 7_897
    }

    fun ageYears(registered: LocalDate?, today: LocalDate): Double? =
        registered?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) / 365.25 }

    private fun midValue(newPrice: Double, ageYears: Double, odometerKm: Int?): Double =
        FairPriceEstimator.estimate(newPrice, ageYears, odometerKm).let { (it.lowEstimate + it.highEstimate) / 2 }

    private fun parse(date: String): LocalDate? = runCatching { LocalDate.parse(date) }.getOrNull()

    /** Sum of entries over the last year, scaled up to a year when they cover less. Null when they cover under four weeks. */
    private fun annualised(entries: List<Pair<LocalDate, Int>>, today: LocalDate, minSpanDays: Long = MIN_SPAN_DAYS): Int? {
        val inYear = entries.filter { it.first.isAfter(today.minusDays(366)) && !it.first.isAfter(today) }
        if (inYear.isEmpty()) return null
        val span = ChronoUnit.DAYS.between(inYear.minOf { it.first }, today).coerceAtMost(365)
        if (span < minSpanDays) return null
        return (inYear.sumOf { it.second } * 365.0 / span).roundToInt()
    }

    fun kmPerMonth(data: OwnerCarData, car: WalletCar, today: LocalDate): KmPerMonth {
        data.monthlyKm?.let { return KmPerMonth(it, KmSource.OWNER) }
        car.tripKmPerMonth?.takeIf { it > 0 }?.let { return KmPerMonth(it.roundToInt(), KmSource.TRIPS) }
        val age = ageYears(car.registered, today)
        if (car.odometerKm != null && car.odometerKm > 0 && age != null && age >= 0.5) {
            return KmPerMonth((car.odometerKm / (age * 12)).roundToInt(), KmSource.ODOMETER)
        }
        return KmPerMonth(1_000, KmSource.GUESS)
    }

    /** The price actually paid per litre, from fill-ups that have both amount and litres. */
    fun fuelPrice(data: OwnerCarData, fuelType: String?): Double {
        val prices = data.fuel.mapNotNull { f -> f.litres?.takeIf { it > 0 }?.let { f.amountInr / it } }.filter { it in 50.0..160.0 }.sorted()
        return if (prices.isEmpty()) defaultFuelPrice(fuelType) else prices[prices.size / 2]
    }

    fun summary(data: OwnerCarData, car: WalletCar, today: LocalDate): WalletSummary {
        val km = kmPerMonth(data, car, today)
        val age = ageYears(car.registered, today)
        val price = fuelPrice(data, car.fuelType)
        val kmpl = car.measuredKmpl ?: OwnerInsights.fuelMileage(data.fuel) ?: car.claimedKmpl?.let { it * REAL_WORLD_SHARE } ?: defaultKmpl(car.fuelType)

        val fuelEntries = data.fuel.mapNotNull { f -> parse(f.date)?.let { it to f.amountInr } }
        val fuelActual = annualised(fuelEntries, today)?.takeIf { fuelEntries.size >= 2 }
        val kmText = when (km.source) {
            KmSource.OWNER -> "%,d km a month".format(km.km)
            KmSource.TRIPS -> "%,d km a month from your trips".format(km.km)
            KmSource.ODOMETER -> "~%,d km a month".format(km.km)
            KmSource.GUESS -> "If you drive ~%,d km a month".format(km.km)
        }
        val fuel = if (fuelActual != null) CostLine(CostKind.FUEL, fuelActual, true, "From your fill-ups")
        else CostLine(CostKind.FUEL, (km.km * 12 / kmpl * price).roundToInt(), false, "$kmText at %.1f km/l, ₹%.0f/L".format(kmpl, price))

        val insurancePaid = data.expenses.filter { it.category == ExpenseCategory.INSURANCE }
            .mapNotNull { e -> parse(e.date)?.takeIf { it.isAfter(today.minusDays(366)) }?.let { it to e.amountInr } }
            .maxByOrNull { it.first }
        val insurance = when {
            insurancePaid != null -> CostLine(CostKind.INSURANCE, insurancePaid.second, true, "Your premium")
            car.newPrice != null && age != null -> {
                val idv = midValue(car.newPrice, age, car.odometerKm)
                val premium = ((idv * 0.02 + thirdPartyPremium(car.engineCc)) * GST / 500).roundToInt() * 500
                CostLine(CostKind.INSURANCE, premium, false, "Estimated from your car's value")
            }
            else -> CostLine(CostKind.INSURANCE, 0, false, "Add your premium")
        }

        val serviceEntries = data.services.mapNotNull { s -> s.costInr?.let { c -> parse(s.date)?.let { it to c } } } +
            data.expenses.filter { it.category == ExpenseCategory.SERVICE || it.category == ExpenseCategory.PARTS }.mapNotNull { e -> parse(e.date)?.let { it to e.amountInr } }
        val servicePaid = serviceEntries.filter { it.first.isAfter(today.minusDays(366)) }.sumOf { it.second }
        val service = if (servicePaid > 0) CostLine(CostKind.SERVICE, servicePaid, true, "Your bills this past year")
        else {
            val rate = when {
                age == null || age < 3 -> 0.006
                age < 6 -> 0.008
                else -> 0.010
            }
            val estimate = car.newPrice?.let { (it * rate).coerceIn(4_000.0, 40_000.0) } ?: 8_000.0
            CostLine(CostKind.SERVICE, ((estimate / 500).roundToInt() * 500), false, age?.let { "Typical for a %d-year-old car of this price".format(it.roundToInt().coerceAtLeast(1)) } ?: "Typical yearly service")
        }

        val otherKinds = setOf(ExpenseCategory.TOLL, ExpenseCategory.PARKING, ExpenseCategory.WASH, ExpenseCategory.PUC, ExpenseCategory.OTHER)
        val otherEntries = data.expenses.filter { it.category in otherKinds }.mapNotNull { e -> parse(e.date)?.let { it to e.amountInr } }
        val otherYear = annualised(otherEntries, today) ?: otherEntries.filter { it.first.isAfter(today.minusDays(366)) }.sumOf { it.second }
        val other = CostLine(CostKind.OTHER, otherYear, otherYear > 0, if (otherYear > 0) "From your payments" else "Fills in as you pay")

        val yearStart = LocalDate.of(today.year, 1, 1)
        fun thisYear(date: String) = parse(date)?.let { !it.isBefore(yearStart) && !it.isAfter(today) } == true
        val spent = data.fuel.filter { thisYear(it.date) }.sumOf { it.amountInr } +
            data.expenses.filter { thisYear(it.date) }.sumOf { it.amountInr } +
            data.services.filter { thisYear(it.date) }.sumOf { it.costInr ?: 0 }

        val valueLost = if (car.newPrice != null && age != null) {
            (midValue(car.newPrice, age, car.odometerKm) - midValue(car.newPrice, age + 1, car.odometerKm?.plus(km.km * 12))).roundToInt().coerceAtLeast(0)
        } else null

        return WalletSummary(listOf(fuel, insurance, service, other), km, kmpl, price, spent, valueLost, age)
    }

    /** "₹1.1 L" from a lakh up, "₹48,200" below. */
    fun short(amount: Int): String =
        if (amount >= 100_000) "₹%.1f L".format(java.util.Locale.US, amount / 100_000.0) else OwnerInsights.inr(amount)
}
