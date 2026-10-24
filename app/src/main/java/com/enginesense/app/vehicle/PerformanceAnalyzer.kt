package com.enginesense.app.vehicle

/** One polled vehicle-speed sample during a performance test run. */
data class SpeedSample(val elapsedMs: Long, val speedKmh: Int)

/** A completed timed/measured segment, or null fields if the run never actually produced it — never a guessed number. */
data class PerformanceResult(
    val zeroToSixtyMs: Long?,
    val zeroToSixtyDistanceM: Double?,
    val sixtyToZeroMs: Long?,
    val sixtyToZeroDistanceM: Double?,
    val hundredMetreMs: Long?
)

/**
 * Turns a raw stream of (time, speed) samples from a live OBD poll into the three measured
 * figures the Performance screen shows. Pure math, no transport/Bluetooth dependency, so
 * it's fully unit-testable with synthetic speed traces — the hard-to-test part (polling a
 * real adapter as fast as possible) stays isolated in the ViewModel.
 *
 * Distance is estimated by trapezoidal integration of the speed trace (no GPS in this app).
 * That's the same approach budget OBD performance apps use when GPS isn't available — less
 * precise than GPS, bounded by how fast the adapter answers each poll, but it's real data
 * from the car, not a guess.
 */
object PerformanceAnalyzer {

    private const val STANDSTILL_KMH = 2
    private const val TARGET_KMH = 60

    fun analyze(samples: List<SpeedSample>): PerformanceResult {
        val sorted = samples.sortedBy { it.elapsedMs }

        val accel = accelerationWindow(sorted)
        val brake = brakingWindow(sorted)
        val hundredM = hundredMetreTime(sorted)

        return PerformanceResult(
            zeroToSixtyMs = accel?.let { it.second.elapsedMs - it.first.elapsedMs },
            zeroToSixtyDistanceM = accel?.let { distanceBetween(sorted, it.first, it.second) },
            sixtyToZeroMs = brake?.let { it.second.elapsedMs - it.first.elapsedMs },
            sixtyToZeroDistanceM = brake?.let { distanceBetween(sorted, it.first, it.second) },
            hundredMetreMs = hundredM
        )
    }

    /** First standstill sample followed later by the first sample reaching 60 km/h, if the run ever did both in that order. */
    private fun accelerationWindow(samples: List<SpeedSample>): Pair<SpeedSample, SpeedSample>? {
        val start = samples.firstOrNull { it.speedKmh <= STANDSTILL_KMH } ?: return null
        val end = samples.firstOrNull { it.elapsedMs > start.elapsedMs && it.speedKmh >= TARGET_KMH } ?: return null
        return start to end
    }

    /** First sample reaching 60 km/h, followed later by the next time speed drops back to a standstill — a braking event. */
    private fun brakingWindow(samples: List<SpeedSample>): Pair<SpeedSample, SpeedSample>? {
        val start = samples.firstOrNull { it.speedKmh >= TARGET_KMH } ?: return null
        val end = samples.firstOrNull { it.elapsedMs > start.elapsedMs && it.speedKmh <= STANDSTILL_KMH } ?: return null
        return start to end
    }

    /** Elapsed time from a standstill start until cumulative integrated distance first reaches 100m. */
    private fun hundredMetreTime(samples: List<SpeedSample>): Long? {
        val start = samples.firstOrNull { it.speedKmh <= STANDSTILL_KMH } ?: return null
        val fromStart = samples.filter { it.elapsedMs >= start.elapsedMs }
        if (fromStart.size < 2) return null

        var distanceM = 0.0
        for (i in 1 until fromStart.size) {
            val prev = fromStart[i - 1]
            val curr = fromStart[i]
            distanceM += segmentDistanceM(prev, curr)
            if (distanceM >= 100.0) return curr.elapsedMs - start.elapsedMs
        }
        return null
    }

    private fun distanceBetween(all: List<SpeedSample>, from: SpeedSample, to: SpeedSample): Double {
        val window = all.filter { it.elapsedMs in from.elapsedMs..to.elapsedMs }
        var distanceM = 0.0
        for (i in 1 until window.size) {
            distanceM += segmentDistanceM(window[i - 1], window[i])
        }
        return distanceM
    }

    /** Trapezoidal rule: average speed over the interval (km/h -> m/s) times elapsed seconds. */
    private fun segmentDistanceM(a: SpeedSample, b: SpeedSample): Double {
        val dtSeconds = (b.elapsedMs - a.elapsedMs) / 1000.0
        if (dtSeconds <= 0) return 0.0
        val avgSpeedMs = ((a.speedKmh + b.speedKmh) / 2.0) / 3.6
        return avgSpeedMs * dtSeconds
    }
}
