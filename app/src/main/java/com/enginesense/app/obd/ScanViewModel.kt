package com.enginesense.app.obd

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScanState {
    data object Idle : ScanState
    data object Connecting : ScanState
    data object ReadingCodes : ScanState
    data class Result(
        val dtcs: List<ExplainedDtc>,
        val rpm: Int?,
        val coolantTempC: Int?,
        val speedKmh: Int?
    ) : ScanState
    data class Error(val message: String) : ScanState
}

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DtcRepository(application)
    private val unresolvedLogger = UnresolvedCodeLogger.getInstance(application)
    private var client: Elm327Client? = null

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    fun pairedDevices(): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return try {
            adapter.bondedDevices?.toList() ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun connectAndRead(device: BluetoothDevice, vehicleBrand: String?) {
        viewModelScope.launch {
            _state.update { ScanState.Connecting }
            try {
                val result = withContext(Dispatchers.IO) {
                    val c = Elm327Client(device)
                    c.connect()
                    client = c

                    val codes = c.readStoredDtcs()
                    val rpm = runCatching { c.readRpm() }.getOrNull()
                    val temp = runCatching { c.readCoolantTempC() }.getOrNull()
                    val speed = runCatching { c.readSpeedKmh() }.getOrNull()

                    Triple(codes, rpm, Pair(temp, speed))
                }
                _state.update { ScanState.ReadingCodes }
                val explained = repository.explainAll(result.first, vehicleBrand)
                _state.update {
                    ScanState.Result(
                        dtcs = explained,
                        rpm = result.second,
                        coolantTempC = result.third.first,
                        speedKmh = result.third.second
                    )
                }
            } catch (e: Exception) {
                _state.update { ScanState.Error(e.message ?: "Couldn't connect to the dongle.") }
            }
        }
    }

    fun clearCodes() {
        viewModelScope.launch {
            val c = client ?: return@launch
            try {
                withContext(Dispatchers.IO) { c.clearDtcs() }
                _state.update { ScanState.Idle }
            } catch (e: Exception) {
                _state.update { ScanState.Error(e.message ?: "Couldn't clear codes.") }
            }
        }
    }

    fun unresolvedCount(): Int = unresolvedLogger.count()

    fun unresolvedLogCsv(): String = unresolvedLogger.exportAsCsv()

    fun clearUnresolvedLog() = unresolvedLogger.clear()

    override fun onCleared() {
        client?.close()
        super.onCleared()
    }
}
