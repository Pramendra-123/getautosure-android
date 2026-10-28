package com.enginesense.app.vehiclelookup

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enginesense.app.auth.GoogleIdTokenCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PurchaseRcState {
    data object Idle : PurchaseRcState()
    data object Looking : PurchaseRcState()
    data class Found(val result: RcLookupResult) : PurchaseRcState()
    data class Failed(val message: String) : PurchaseRcState()
}

/**
 * The Used Car Check's RTO lookup: official year/validity for the car being bought, plus whether
 * its OBD VIN matches the RC's chassis number (decided server-side — the app never sees the
 * chassis). Separate from [VehicleLookupViewModel] because that one is the buyer's own-car
 * onboarding prefill, with its own failure wording and a result that must map to a known model.
 */
class PurchaseRcCheckViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenCache = GoogleIdTokenCache()
    private var authUiContext: Context? = null

    private val repository: VehicleLookupRepository? =
        if (VehicleLookupBackendConfig.isConfigured) {
            VehicleLookupRepository(
                endpointUrl = VehicleLookupBackendConfig.ENDPOINT_URL,
                idTokenProvider = {
                    val uiContext = authUiContext
                        ?: throw VehicleLookupNotAuthenticatedException(IllegalStateException("no UI context available for sign-in"))
                    tokenCache.get(uiContext)
                }
            )
        } else {
            null
        }

    private val _state = MutableStateFlow<PurchaseRcState>(PurchaseRcState.Idle)
    val state: StateFlow<PurchaseRcState> = _state.asStateFlow()

    fun check(uiContext: Context, rawPlate: String, vin: String?) {
        val plate = RcPrefillMapper.normalizePlate(rawPlate)
        if (!RcPrefillMapper.isValidPlate(plate)) {
            _state.value = PurchaseRcState.Failed("That doesn't look like a registration number. Check the number plate, e.g. MH12AB1234.")
            return
        }
        val repo = repository
        if (repo == null) {
            _state.value = PurchaseRcState.Failed("Checking RTO records isn't available yet.")
            return
        }
        if (_state.value == PurchaseRcState.Looking) return

        _state.value = PurchaseRcState.Looking
        viewModelScope.launch {
            authUiContext = uiContext
            val result = try {
                repo.lookup(plate, vin)
            } finally {
                authUiContext = null
            }
            _state.value = result.fold(
                onSuccess = { PurchaseRcState.Found(it) },
                onFailure = { PurchaseRcState.Failed(messageFor(it)) }
            )
        }
    }

    companion object {
        fun messageFor(error: Throwable): String = when (error) {
            is VehicleLookupNotAuthenticatedException -> "Sign in with Google to check RTO records."
            is VehicleLookupNetworkException -> "Couldn't reach our servers. Check your internet connection and try again."
            is VehicleLookupHttpException -> when (error.code) {
                "invalid_registration", "lookup_failed", "rate_limited" -> error.serverMessage ?: "We couldn't look up that number."
                "unauthorized" -> "Your sign-in has expired. Sign in again to check RTO records."
                else -> "Checking RTO records isn't working right now. Try again later."
            }
            else -> "Something went wrong checking RTO records. Try again later."
        }

        private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

        /** The RTO's "2019-11" as "Nov 2019"; anything else is shown as the RTO wrote it. */
        fun formatMonth(rto: String?): String? {
            if (rto == null) return null
            val match = Regex("""^(\d{4})-(\d{1,2})""").find(rto) ?: return rto
            val month = match.groupValues[2].toInt()
            return if (month in 1..12) "${MONTHS[month - 1]} ${match.groupValues[1]}" else rto
        }
    }
}
