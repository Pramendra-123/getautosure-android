package com.enginesense.app.vehicle

/**
 * How a VERIFIED reading compares to what's normal for a warmed-up, healthy vehicle — not
 * just "is this physically possible" (see [PidId.plausibleRange], which only guards against
 * sensor/decode garbage) but "is this number actually good".
 */
enum class SensorRating { GOOD, WATCH, ATTENTION, NOT_RATED }

data class SensorBenchmark(
    val typicalRange: ClosedFloatingPointRange<Double>,
    /** One-line plain-language explanation of the range, shown next to the reading. */
    val note: String,
    /**
     * True for PIDs where "normal" depends on what the car is doing right now — how hard
     * you're pressing the pedal, whether you're idling or accelerating — not just whether
     * the car is healthy (engine load, RPM, throttle, manifold pressure). These never get a
     * colored verdict, only the reference range as context: a fixed "normal" number would be
     * actively misleading (14% throttle is correct at a gentle cruise and also correct
     * easing off — there's no single right answer without knowing what you're doing with the
     * pedal). Composite judgment calls like that belong to the AI report/chat, which reads
     * the whole snapshot together, not a single-sensor badge.
     */
    val contextDependent: Boolean = false
)

/**
 * Typical operating ranges sourced from widely published OBD2/service-manual reference
 * values (e.g. coolant 80-105°C, alternator charging 13.5-14.8V, fuel trim within ±10%).
 * Deliberately short: a PID without a defensible, mostly context-independent "healthy"
 * number is left out entirely rather than given a guessed range — same "never a guess"
 * discipline as the rest of this screen.
 */
private val BENCHMARKS: Map<PidId, SensorBenchmark> = mapOf(
    PidId.COOLANT_TEMP_C to SensorBenchmark(
        80.0..105.0, "A warmed-up engine normally runs 80-105°C. Below that it hasn't warmed up yet; above that, it's running hot."
    ),
    PidId.CONTROL_MODULE_VOLTAGE to SensorBenchmark(
        13.5..14.8, "13.5-14.8V with the engine running means the alternator is charging properly."
    ),
    PidId.SHORT_TERM_FUEL_TRIM_1 to SensorBenchmark(
        -10.0..10.0, "Within ±10% is normal fine-tuning; consistently beyond that suggests a vacuum leak or dirty injector."
    ),
    PidId.LONG_TERM_FUEL_TRIM_1 to SensorBenchmark(
        -10.0..10.0, "Within ±10% is normal; a bigger number means the computer is compensating for something longer-term."
    ),
    PidId.ENGINE_OIL_TEMP_C to SensorBenchmark(
        80.0..120.0, "80-120°C once warmed up is typical for engine oil."
    ),
    PidId.ENGINE_LOAD to SensorBenchmark(
        0.0..40.0, "Under 40% is typical at idle or a gentle cruise — higher is expected and normal under acceleration or load.", contextDependent = true
    ),
    PidId.RPM to SensorBenchmark(
        600.0..1000.0, "600-1000 rpm is typical idle speed — higher is expected and normal while driving.", contextDependent = true
    ),
    PidId.THROTTLE_POSITION to SensorBenchmark(
        0.0..20.0, "Near 0% at idle — opens with however far you're pressing the accelerator, so there's no single 'correct' number.", contextDependent = true
    ),
    PidId.INTAKE_MANIFOLD_PRESSURE to SensorBenchmark(
        20.0..50.0, "Roughly 20-50 kPa at idle for most engines — rises with load and varies with altitude.", contextDependent = true
    )
)

fun PidId.benchmark(): SensorBenchmark? = BENCHMARKS[this]

/** [SensorRating.NOT_RATED] for any PID without a benchmark, or whose benchmark is context-dependent. */
fun PidId.rate(value: Double): SensorRating {
    val benchmark = benchmark()?.takeUnless { it.contextDependent } ?: return SensorRating.NOT_RATED
    if (value in benchmark.typicalRange) return SensorRating.GOOD
    val span = benchmark.typicalRange.endInclusive - benchmark.typicalRange.start
    val margin = span * 0.25
    val widened = (benchmark.typicalRange.start - margin)..(benchmark.typicalRange.endInclusive + margin)
    return if (value in widened) SensorRating.WATCH else SensorRating.ATTENTION
}
