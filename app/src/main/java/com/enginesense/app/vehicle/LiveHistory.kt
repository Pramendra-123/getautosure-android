package com.enginesense.app.vehicle

/** One reading of one sensor, [elapsedMs] after Live Data started. */
data class LiveSample(val elapsedMs: Long, val value: Double)

/** What the Live Data graphs draw: each sensor's recent readings, and the time "now" is, so slow-polled sensors line up with fast ones. */
data class LiveHistorySnapshot(val nowMs: Long = 0, val series: Map<PidId, List<LiveSample>> = emptyMap())

/**
 * The last [windowMs] of readings per sensor, for the scrolling (ECG-style) graphs on Live Data.
 * Only readings the car actually sent this cycle go in — a slow-polled sensor's stale value isn't
 * repeated, so its line shows the real gaps rather than an invented flat stretch. Pure Kotlin,
 * fed by [com.enginesense.app.GetAutoSureViewModel.startLiveData], so it's unit-tested directly.
 */
class LiveHistory(private val windowMs: Long = DEFAULT_WINDOW_MS) {
    private val samples = mutableMapOf<PidId, ArrayDeque<LiveSample>>()
    private var nowMs = 0L

    fun add(fresh: Map<PidId, PidReading>, elapsedMs: Long) {
        nowMs = elapsedMs
        fresh.forEach { (pid, reading) ->
            val value = reading.value
            if (reading.status == PidStatus.VERIFIED && value != null) {
                samples.getOrPut(pid) { ArrayDeque() }.addLast(LiveSample(elapsedMs, value))
            }
        }
        val cutoff = elapsedMs - windowMs
        samples.values.forEach { queue -> while (queue.isNotEmpty() && queue.first().elapsedMs < cutoff) queue.removeFirst() }
    }

    fun snapshot(): LiveHistorySnapshot = LiveHistorySnapshot(nowMs, samples.mapValues { it.value.toList() })

    companion object {
        const val DEFAULT_WINDOW_MS = 60_000L
    }
}

/** The chip on a Live Data graph. Mirrors the reports' wording so a reading means the same thing everywhere. */
enum class LiveStatus { NORMAL, TYPICAL, VARIES_WITH_DRIVING, WARMING_UP, WATCH, CHECK }

/**
 * Null when there's nothing honest to say about this sensor on its own (no published normal
 * range). Below the warm range, coolant/oil read "warming up", not a fault; readings that depend
 * on how the car is being driven (RPM, load, throttle, manifold pressure) are only ever "typical"
 * or "varies with driving", never a warning — see [SensorBenchmark.contextDependent].
 */
fun liveStatus(pid: PidId, value: Double): LiveStatus? {
    val benchmark = pid.benchmark() ?: return null
    if ((pid == PidId.COOLANT_TEMP_C || pid == PidId.ENGINE_OIL_TEMP_C) && value < benchmark.typicalRange.start) return LiveStatus.WARMING_UP
    if (benchmark.contextDependent) return if (value in benchmark.typicalRange) LiveStatus.TYPICAL else LiveStatus.VARIES_WITH_DRIVING
    return when (pid.rate(value)) {
        SensorRating.GOOD -> LiveStatus.NORMAL
        SensorRating.WATCH -> LiveStatus.WATCH
        SensorRating.ATTENTION -> LiveStatus.CHECK
        SensorRating.NOT_RATED -> null
    }
}
