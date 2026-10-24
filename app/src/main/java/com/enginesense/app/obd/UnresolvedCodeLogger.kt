package com.enginesense.app.obd

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

enum class UnresolvedReason { UNKNOWN_CODE, MANUFACTURER_GAP }

data class UnresolvedEntry(
    val code: String,
    val manufacturer: String?,
    val reason: UnresolvedReason,
    val timestampMillis: Long
)

/**
 * Local, on-device log of DTCs the app couldn't explain well — either not in any database,
 * or a manufacturer-specific-looking code (Pxxxx/Bxxxx/Cxxxx/Uxxxx with second digit '1')
 * where only a generic fallback was available. No accounts/backend yet, so this stays on
 * the device; use "Share log" to hand it to yourself for review. Stores only the code,
 * manufacturer, and a timestamp — no location, VIN, or other identifying data.
 */
class UnresolvedCodeLogger(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                code TEXT NOT NULL,
                manufacturer TEXT,
                reason TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun log(code: String, manufacturer: String?, reason: UnresolvedReason) {
        val values = ContentValues().apply {
            put("code", code.uppercase())
            put("manufacturer", manufacturer)
            put("reason", reason.name)
            put("timestamp", System.currentTimeMillis())
        }
        writableDatabase.insert(TABLE, null, values)
    }

    fun getAll(): List<UnresolvedEntry> {
        val entries = mutableListOf<UnresolvedEntry>()
        readableDatabase.rawQuery(
            "SELECT code, manufacturer, reason, timestamp FROM $TABLE ORDER BY timestamp DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                entries.add(
                    UnresolvedEntry(
                        code = cursor.getString(0),
                        manufacturer = cursor.getString(1),
                        reason = UnresolvedReason.valueOf(cursor.getString(2)),
                        timestampMillis = cursor.getLong(3)
                    )
                )
            }
        }
        return entries
    }

    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    fun clear() {
        writableDatabase.delete(TABLE, null, null)
    }

    fun exportAsCsv(): String {
        val header = "code,manufacturer,reason,timestamp"
        val rows = getAll().joinToString("\n") { entry ->
            "${entry.code},${entry.manufacturer ?: ""},${entry.reason},${entry.timestampMillis}"
        }
        return if (rows.isEmpty()) header else "$header\n$rows"
    }

    companion object {
        private const val DB_NAME = "unresolved_codes.db"
        private const val DB_VERSION = 1
        private const val TABLE = "unresolved_codes"

        @Volatile private var instance: UnresolvedCodeLogger? = null

        fun getInstance(context: Context): UnresolvedCodeLogger =
            instance ?: synchronized(this) {
                instance ?: UnresolvedCodeLogger(context).also { instance = it }
            }

        /** True if a code's second character marks it manufacturer-specific (P1xxx, B1xxx, C1xxx, U1xxx). */
        fun looksManufacturerSpecific(code: String): Boolean =
            code.length >= 2 && code[1] == '1'
    }
}
