package com.enginesense.app.drivelab

import android.content.Context
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * One reading from the always-connected test recorder. Every field is null when the car didn't
 * answer it on that cycle — engine-off samples carry only [adapterVolts] (the dongle's own
 * battery-voltage reading, which works with the ECU asleep) and [rpm] = 0 or null.
 */
data class DriveSample(
    val timeMillis: Long,
    val rpm: Double?,
    val speedKmh: Double?,
    /** Measured (MAF) or estimated from manifold pressure — see [com.enginesense.app.vehicle.AirFlowEstimator]. */
    val airGramsPerSec: Double?,
    val throttlePercent: Double?,
    val engineLoadPercent: Double?,
    val coolantC: Double?,
    /** PID 0x42, read through the ECU — only while the ignition is on. */
    val moduleVolts: Double?,
    /** ELM327 "ATRV": voltage at the OBD port pin, readable even with the car off. */
    val adapterVolts: Double?,
    val fuelLevelPercent: Double?,
    val distanceSinceClearedKm: Double?,
    val odometerKm: Double?,
    /** Short-term + long-term fuel trim, bank 1 — read with the slow set, so null on most samples. */
    val fuelTrimPercent: Double? = null
) {
    val engineOn: Boolean get() = (rpm ?: 0.0) > 0.0

    fun toCsv(): String = listOf(
        timeMillis, rpm, speedKmh, airGramsPerSec, throttlePercent, engineLoadPercent, coolantC,
        moduleVolts, adapterVolts, fuelLevelPercent, distanceSinceClearedKm, odometerKm, fuelTrimPercent
    ).joinToString(",") { v -> when (v) { null -> ""; is Double -> "%.3f".format(java.util.Locale.US, v); else -> v.toString() } }

    companion object {
        const val CSV_HEADER = "time_ms,rpm,speed_kmh,air_gps,throttle_pct,load_pct,coolant_c,module_v,adapter_v,fuel_level_pct,dist_since_cleared_km,odometer_km,fuel_trim_pct"

        fun fromCsv(line: String): DriveSample? {
            val f = line.split(",")
            if (f.size < 12) return null
            fun d(i: Int) = f[i].takeIf { it.isNotBlank() }?.toDoubleOrNull()
            val t = f[0].toLongOrNull() ?: return null
            // Rows written before fuel trim was recorded have 12 columns.
            return DriveSample(t, d(1), d(2), d(3), d(4), d(5), d(6), d(7), d(8), d(9), d(10), d(11), if (f.size > 12) d(12) else null)
        }
    }
}

/** Something that happened to the connection or the car: connected, engine_on, engine_off, error… */
data class DriveEvent(val timeMillis: Long, val type: String, val detail: String = "") {
    fun toCsv(): String = "$timeMillis,$type,${detail.replace(",", ";").replace("\n", " ")}"

    companion object {
        fun fromCsv(line: String): DriveEvent? {
            val f = line.split(",", limit = 3)
            val t = f.getOrNull(0)?.toLongOrNull() ?: return null
            return DriveEvent(t, f.getOrElse(1) { "" }, f.getOrElse(2) { "" })
        }
    }
}

/**
 * Plain CSV files under filesDir/drivelab — append-only so a crash or a killed service loses at
 * most the line being written, and so the raw files can be shared as-is for analysis. This is a
 * test tool: nothing here leaves the phone unless the user taps Export.
 */
class DriveLabStore(context: Context) {

    private val dir = File(context.filesDir, "drivelab").apply { mkdirs() }
    private val samplesFile = File(dir, "samples.csv")
    private val eventsFile = File(dir, "events.csv")
    private val supportFile = File(dir, "supported_pids.csv")

    @Synchronized
    fun appendSample(sample: DriveSample) {
        if (!samplesFile.exists()) samplesFile.writeText(DriveSample.CSV_HEADER + "\n")
        samplesFile.appendText(sample.toCsv() + "\n")
    }

    @Synchronized
    fun appendEvent(event: DriveEvent) {
        eventsFile.appendText(event.toCsv() + "\n")
    }

    /** "pid,name,status,value" for every PID, written the first time the engine runs on a connection. */
    @Synchronized
    fun saveSupport(rows: List<String>) {
        supportFile.writeText("pid,name,status,value\n" + rows.joinToString("\n") + "\n")
    }

    @Synchronized
    fun loadSamples(sinceMillis: Long = 0L): List<DriveSample> {
        if (!samplesFile.exists()) return emptyList()
        return samplesFile.useLines { lines ->
            lines.drop(1).mapNotNull { DriveSample.fromCsv(it) }.filter { it.timeMillis >= sinceMillis }.toList()
        }
    }

    @Synchronized
    fun loadEvents(): List<DriveEvent> =
        if (!eventsFile.exists()) emptyList() else eventsFile.readLines().mapNotNull { DriveEvent.fromCsv(it) }

    @Synchronized
    fun loadSupport(): List<List<String>> =
        if (!supportFile.exists()) emptyList() else supportFile.readLines().drop(1).filter { it.isNotBlank() }.map { it.split(",", limit = 4) }

    @Synchronized
    fun clear() {
        samplesFile.delete(); eventsFile.delete(); supportFile.delete()
    }

    fun sizeBytes(): Long = listOf(samplesFile, eventsFile, supportFile).sumOf { if (it.exists()) it.length() else 0L }

    /** Zips the raw files into cache/reports (the FileProvider's shared folder) for the share sheet. */
    @Synchronized
    fun exportZip(cacheDir: File): File {
        val outDir = File(cacheDir, "reports").apply { mkdirs() }
        val zip = File(outDir, "drive-lab-${System.currentTimeMillis()}.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            listOf(samplesFile, eventsFile, supportFile).filter { it.exists() }.forEach { f ->
                out.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        return zip
    }
}
