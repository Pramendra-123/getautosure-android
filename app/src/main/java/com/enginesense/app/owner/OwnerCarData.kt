package com.enginesense.app.owner

/** The things with an expiry date that an owner has to renew. */
enum class RenewalKind(val label: String) {
    INSURANCE("Insurance"),
    PUC("PUC"),
    ROAD_TAX("Road tax"),
    FITNESS("Fitness"),
    LICENCE("Driving licence")
}

data class ServiceEntry(
    val id: Long,
    /** ISO date, "2026-09-12". */
    val date: String,
    val odometerKm: Int?,
    val work: String,
    val costInr: Int?
)

/** Where a fill-up or expense came from — shown under it in the wallet. */
enum class EntrySource(val label: String) { MANUAL("Added by you"), SHARE("From a shared message"), ALERT("From a payment alert"), FILL_UP("Spotted by the scanner"), AI("Sorted by AI from a payment alert") }

data class FuelEntry(
    val id: Long,
    val date: String,
    val litres: Double?,
    val amountInr: Int,
    val odometerKm: Int?,
    /** Filled to the brim — mileage is worked out between two full tanks. */
    val fullTank: Boolean,
    val source: EntrySource = EntrySource.MANUAL
)

enum class ExpenseCategory(val label: String) {
    SERVICE("Service"), REPAIR("Repair"), PARTS("Parts"), INSURANCE("Insurance"), TOLL("Toll"), PARKING("Parking"), WASH("Wash"), PUC("PUC"), OTHER("Other")
}

data class ExpenseEntry(
    val id: Long,
    val date: String,
    val category: ExpenseCategory,
    val amountInr: Int,
    val note: String = "",
    val source: EntrySource = EntrySource.MANUAL
)

/**
 * Everything the owner tells us about one car that a scan can't: renewal dates they changed or
 * added, services, fill-ups and other spending. Kept on the phone, one file per car.
 */
data class OwnerCarData(
    /** Dates the owner set by hand (ISO). They win over the RTO record, which goes stale after a renewal. */
    val renewalDates: Map<RenewalKind, String> = emptyMap(),
    val remindersOn: Boolean = true,
    val services: List<ServiceEntry> = emptyList(),
    /** The owner skipped "When was the last service?" — don't ask again. */
    val lastServiceQuestionSkipped: Boolean = false,
    val serviceIntervalKm: Int = 10_000,
    val serviceIntervalMonths: Int = 12,
    val fuel: List<FuelEntry> = emptyList(),
    val expenses: List<ExpenseEntry> = emptyList(),
    /** Fuel tank size, asked once the first time a fill-up is spotted. */
    val tankLitres: Int? = null,
    /** Spotted fill-ups the owner already answered (saved or said "not a fill-up"), by when they were spotted. */
    val handledFills: List<Long> = emptyList(),
    /** The owner's answer to "How much do you drive a month?" — wins over every estimate. */
    val monthlyKm: Int? = null,
    /** Payment alerts already added, so the same alert posted twice is added once. Newest last, capped. */
    val capturedKeys: List<String> = emptyList(),
    /** Repairs the owner marked as done, so the next scan can confirm the fault is really gone. */
    val fixedRepairs: List<FixedRepair> = emptyList(),
    /** Merchants already sorted (by the owner or AI): merchant key → category name, or "NOT_CAR". Sorted on the phone next time. */
    val merchantCategories: Map<String, String> = emptyMap(),
    /** The owner turned on AI sorting of payment alerts the rules can't sort. Off by default. */
    val aiSortingOn: Boolean = false,
    /** Alerts waiting for AI or for the owner's answer — masked text only. */
    val pendingPayments: List<PendingPayment> = emptyList()
)

/**
 * A fault the owner says was repaired. [afterScanMillis] is the scan it was marked after; a later
 * scan without [code] confirms the fix ([verified]). [paidInr] also went into the Wallet as a Repair.
 */
data class FixedRepair(
    val code: String,
    val title: String,
    val date: String,
    val paidInr: Int?,
    val afterScanMillis: Long,
    val verified: Boolean = false
)
