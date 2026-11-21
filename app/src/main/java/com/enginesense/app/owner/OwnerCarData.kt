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

data class FuelEntry(
    val id: Long,
    val date: String,
    val litres: Double?,
    val amountInr: Int,
    val odometerKm: Int?,
    /** Filled to the brim — mileage is worked out between two full tanks. */
    val fullTank: Boolean
)

enum class ExpenseCategory(val label: String) { SERVICE("Service"), PARTS("Parts"), TOLL("Toll"), PARKING("Parking"), WASH("Wash"), OTHER("Other") }

data class ExpenseEntry(
    val id: Long,
    val date: String,
    val category: ExpenseCategory,
    val amountInr: Int,
    val note: String = ""
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
    val handledFills: List<Long> = emptyList()
)
