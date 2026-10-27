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

sealed class LookupState {
    data object Idle : LookupState()
    data object Looking : LookupState()
    data class Found(val result: RcLookupResult, val prefill: RcPrefill) : LookupState()
    /** Every failure offers manual entry — the lookup is a shortcut, never a gate. */
    data class Failed(val message: String) : LookupState()
}

/**
 * Backs [com.enginesense.app.ui.screens.EnterRegistrationScreen]. Same token-cache and bounded
 * auth-context approach as [com.enginesense.app.dealerreport.PublishReportViewModel] (one call at
 * a time here, so no ref-counting needed).
 */
class VehicleLookupViewModel(application: Application) : AndroidViewModel(application) {

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

    private val _state = MutableStateFlow<LookupState>(LookupState.Idle)
    val state: StateFlow<LookupState> = _state.asStateFlow()

    fun lookup(uiContext: Context, rawPlate: String) {
        val plate = RcPrefillMapper.normalizePlate(rawPlate)
        if (!RcPrefillMapper.isValidPlate(plate)) {
            _state.value = LookupState.Failed("That doesn't look like a registration number. Check it and try again, e.g. MH12AB1234.")
            return
        }
        val repo = repository
        if (repo == null) {
            _state.value = LookupState.Failed("Looking up cars by number isn't available yet — please enter your car's details instead.")
            return
        }
        if (_state.value == LookupState.Looking) return

        _state.value = LookupState.Looking
        viewModelScope.launch {
            authUiContext = uiContext
            val result = try {
                repo.lookup(plate)
            } finally {
                authUiContext = null
            }
            _state.value = result.fold(
                onSuccess = { found ->
                    val prefill = RcPrefill.from(found)
                    if (prefill == null) {
                        LookupState.Failed("We found the registration but the RTO record doesn't say which car it is. Please enter the details yourself.")
                    } else {
                        LookupState.Found(found, prefill)
                    }
                },
                onFailure = { LookupState.Failed(messageFor(it)) }
            )
        }
    }

    fun reset() {
        _state.value = LookupState.Idle
    }

    companion object {
        /** What the customer reads for each failure — the server's own message where it wrote one for people, never raw HTTP codes. */
        fun messageFor(error: Throwable): String = when (error) {
            is VehicleLookupNotAuthenticatedException ->
                "Sign in with Google to look up your car by number — or enter the details yourself."
            is VehicleLookupNetworkException ->
                "Couldn't reach our servers. Check your internet connection and try again."
            is VehicleLookupHttpException -> when (error.code) {
                "invalid_registration", "lookup_failed", "rate_limited" ->
                    error.serverMessage ?: "We couldn't look up that number."
                "unauthorized" -> "Your sign-in has expired. Sign in again, or enter the details yourself."
                else -> "Looking up cars by number isn't working right now. Please enter the details yourself."
            }
            else -> "Something went wrong looking up that number. Please enter the details yourself."
        }
    }
}
