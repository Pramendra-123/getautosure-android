package com.enginesense.app.vehicle

/**
 * Outcome of trying to read one PID from the vehicle. This is the backbone of the
 * "supported / verified / unavailable / unsupported" requirement: every reading carries
 * one of these so nothing downstream (rules engine, AI layer, UI) ever has to guess
 * whether a null value means "car doesn't have this sensor" vs "sensor glitched" vs
 * "we never asked".
 */
enum class PidStatus {
    /** Got a value back and it passed plausibility validation for this PID. */
    VERIFIED,

    /** The ECU supports this PID (per the Mode 01 PID 00 bitmask) but returned NO DATA right now. */
    SUPPORTED_NO_DATA,

    /** The ECU does not support this PID at all on this vehicle. */
    UNSUPPORTED,

    /** Got bytes back but they're outside the physically plausible range for this PID — treat as untrustworthy. */
    IMPLAUSIBLE,

    /** Couldn't even talk to the adapter (I/O error, not a vehicle-reported state). */
    TRANSPORT_ERROR
}

data class PidReading(
    val pid: PidId,
    val status: PidStatus,
    val value: Double?,
    val rawBytes: List<Int>?
)

/**
 * STORED (Mode 03) are confirmed faults. PENDING (Mode 07) were seen once but not yet confirmed.
 * PERMANENT (Mode 0A) can't be erased by a scan tool — they stay until the car's own self-test
 * passes, so they survive a seller clearing codes. Only STORED feed findings and the health
 * score; the other two are read for the pre-purchase check.
 */
enum class DtcSource { STORED, PENDING, PERMANENT }

data class DtcReading(
    val code: String,
    val source: DtcSource
)

/**
 * The validated, structured snapshot of a vehicle at one point in time. This is the
 * single source of truth that the rules engine and AI layer consume — never raw ECU
 * bytes, never a partially-parsed adapter string.
 */
data class VehicleSnapshot(
    val timestampMillis: Long,
    val vehicleBrand: String?,
    val readings: Map<PidId, PidReading>,
    val dtcs: List<DtcReading>
) {
    fun reading(pid: PidId): PidReading? = readings[pid]

    fun verifiedValue(pid: PidId): Double? =
        readings[pid]?.takeIf { it.status == PidStatus.VERIFIED }?.value
}
