package com.enginesense.app.obd

import java.io.IOException

/**
 * Hardware-agnostic contract for anything that can speak the ELM327 AT/OBD command
 * protocol: a real Bluetooth SPP dongle, a future wired/BLE adapter, or a simulator.
 * Everything above this layer (normalization, PID decoding, the rules engine, AI
 * contracts) talks only to this interface, never to a concrete transport — so a new
 * OBD hardware adapter plugs in as a second implementation with zero changes above.
 */
interface ObdTransport {

    @Throws(IOException::class)
    fun connect()

    /** Sends a raw AT/OBD command (no trailing \r) and returns the adapter's trimmed text response. */
    @Throws(IOException::class)
    fun sendCommand(command: String): String

    fun close()

    /**
     * Every (command, raw response) pair exchanged so far, oldest first — a diagnostic trail
     * for when a real adapter's response format doesn't match what the simulator exercises.
     * Empty by default; only [Elm327Client] actually records anything, since the simulator's
     * responses are already known exactly (they're the hardcoded fixtures).
     */
    fun commandLog(): List<Pair<String, String>> = emptyList()
}
