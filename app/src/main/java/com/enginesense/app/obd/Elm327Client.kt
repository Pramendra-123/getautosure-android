package com.enginesense.app.obd

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Talks to a generic ELM327 Bluetooth dongle over classic SPP.
 * The device must already be paired in Android's Bluetooth settings —
 * this class only opens a socket to an already-bonded [BluetoothDevice].
 */
class Elm327Client(private val device: BluetoothDevice) : ObdTransport {

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    private val log = mutableListOf<Pair<String, String>>()
    override fun commandLog(): List<Pair<String, String>> = log.toList()

    @Throws(IOException::class)
    override fun connect() {
        val sock = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (e: IOException) {
            fallbackSocket()
        }
        try {
            sock.connect()
        } catch (e: IOException) {
            // Many cheap ELM327 clones don't implement standard SDP lookup correctly.
            sock.close()
            val fallback = fallbackSocket()
            fallback.connect()
            bind(fallback)
            initAdapter()
            return
        }
        bind(sock)
        initAdapter()
    }

    private fun bind(sock: BluetoothSocket) {
        socket = sock
        input = sock.inputStream
        output = sock.outputStream
    }

    @Throws(IOException::class)
    private fun fallbackSocket(): BluetoothSocket {
        // Reflection fallback used widely by OBD2 apps for clones with broken SDP records.
        val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        return method.invoke(device, 1) as BluetoothSocket
    }

    @Throws(IOException::class)
    private fun initAdapter() {
        sendCommand("ATZ")   // reset
        sendCommand("ATE0")  // echo off
        sendCommand("ATL0")  // linefeeds off
        sendCommand("ATH0")  // headers off
        sendCommand("ATSP0") // auto protocol
    }

    /** Sends a raw AT/OBD command and returns the adapter's trimmed text response. */
    @Throws(IOException::class)
    override fun sendCommand(command: String): String {
        val out = output ?: throw IOException("Not connected")
        val inp = input ?: throw IOException("Not connected")

        out.write((command + "\r").toByteArray())
        out.flush()

        val buffer = StringBuilder()
        val readBuf = ByteArray(1)
        while (true) {
            val n = inp.read(readBuf)
            if (n <= 0) break
            val c = readBuf[0].toInt().toChar()
            if (c == '>') break
            buffer.append(c)
        }
        val raw = buffer.toString()
        // Logged with \r/\n made visible (not collapsed to spaces like the parsed return value
        // below) — a multi-line response is exactly the kind of real-hardware quirk the
        // simulator can't exercise, so losing the line breaks here would hide the evidence.
        log.add(command to raw.replace("\r", "\\r").replace("\n", "\\n"))
        if (log.size > 200) log.removeAt(0) // cap memory on long sessions

        return raw.replace("\r", " ").replace("\n", " ").trim()
    }

    /** Reads stored (Mode 03) diagnostic trouble codes. */
    @Throws(IOException::class)
    fun readStoredDtcs(): List<String> {
        val response = sendCommand("03")
        return parseDtcResponse(response)
    }

    /** Clears stored DTCs and the MIL. This changes the vehicle's ECU state — gate behind explicit user confirmation. */
    @Throws(IOException::class)
    fun clearDtcs() {
        sendCommand("04")
    }

    /** Reads engine RPM (Mode 01 PID 0C), or null if unavailable. */
    @Throws(IOException::class)
    fun readRpm(): Int? {
        val bytes = readPidBytes("010C") ?: return null
        if (bytes.size < 2) return null
        return ((bytes[0] * 256) + bytes[1]) / 4
    }

    /** Reads coolant temperature in Celsius (Mode 01 PID 05), or null if unavailable. */
    @Throws(IOException::class)
    fun readCoolantTempC(): Int? {
        val bytes = readPidBytes("0105") ?: return null
        if (bytes.isEmpty()) return null
        return bytes[0] - 40
    }

    /** Reads vehicle speed in km/h (Mode 01 PID 0D), or null if unavailable. */
    @Throws(IOException::class)
    fun readSpeedKmh(): Int? {
        val bytes = readPidBytes("010D") ?: return null
        if (bytes.isEmpty()) return null
        return bytes[0]
    }

    private fun readPidBytes(pidCommand: String): IntArray? {
        val response = sendCommand(pidCommand)
        val tokens = response.split(" ").filter { it.isNotBlank() && it.matches(HEX_BYTE) }
        if (tokens.size < 2) return null
        // Expect: <mode+0x40> <pid> <data...>, e.g. "41 0C 1A F8"
        return tokens.drop(2).map { it.toInt(16) }.toIntArray()
    }

    override fun close() {
        try { socket?.close() } catch (_: IOException) { }
        socket = null
        input = null
        output = null
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val HEX_BYTE = Regex("^[0-9A-Fa-f]{2}$")

        /**
         * Decodes a Mode 03 response like "43 01 33 00 00 00 00" into DTC strings like "P0133".
         * Delegates the actual byte-pair math to [DtcDecoder] so this and the newer
         * [com.enginesense.app.vehicle] pipeline share one implementation.
         */
        fun parseDtcResponse(response: String): List<String> {
            val parsed = ObdResponseParser.parse(response, expectedModeEcho = "43")
            val bytes = (parsed as? ObdRawResult.Ok)?.bytes ?: return emptyList()
            return DtcDecoder.decode(bytes)
        }
    }
}
