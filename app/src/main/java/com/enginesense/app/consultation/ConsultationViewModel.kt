package com.enginesense.app.consultation

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enginesense.app.auth.GoogleIdTokenCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One screen's worth of load state — every screen in this feature follows the same loading/error/success shape, not ad hoc flags per screen. */
sealed class LoadState<out T> {
    object Loading : LoadState<Nothing>()
    data class Success<T>(val data: T) : LoadState<T>()
    data class Error(val message: String) : LoadState<Nothing>()
}

/**
 * Backs the whole "Ask a Real Expert" feature. Talks only to [ExpertRepository] — gated by
 * [ConsultationBackendConfig], same pattern as [com.enginesense.app.vehicle.ai.AiBackendConfig]
 * gates AI Mechanic's remote/local split: [MockExpertRepository] until a real endpoint is
 * configured and deployed, [SupabaseExpertRepository] once it is.
 */
class ConsultationViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenCache = GoogleIdTokenCache()

    private val repository: ExpertRepository =
        if (ConsultationBackendConfig.isConfigured) {
            SupabaseExpertRepository(
                endpointUrl = ConsultationBackendConfig.ENDPOINT_URL,
                // Reads authUiContext below — see its own doc for why this can't just capture
                // `application` the way endpointUrl is captured; same reasoning as
                // GetAutoSureViewModel's own idTokenProvider for AI Mechanic.
                idTokenProvider = {
                    val uiContext = authUiContext
                        ?: throw ConsultationNotAuthenticatedException(IllegalStateException("no UI context available for sign-in"))
                    tokenCache.get(uiContext)
                }
            )
        } else {
            MockExpertRepository()
        }

    // Set only for the duration of one authenticated call (reserveSlot / loadMyBookings /
    // cancelBooking), then cleared — never held longer than that. Same bounded-lifetime
    // tradeoff as GetAutoSureViewModel.authUiContext, same reason: Credential Manager needs an
    // Activity-rooted context, not this ViewModel's own Application context, and a ViewModel
    // must never cache an Activity reference for longer than it has to.
    private var authUiContext: Context? = null

    private val _experts = MutableStateFlow<LoadState<List<Expert>>>(LoadState.Loading)
    val experts: StateFlow<LoadState<List<Expert>>> = _experts.asStateFlow()

    private val _slots = MutableStateFlow<LoadState<List<AvailabilitySlot>>>(LoadState.Loading)
    val slots: StateFlow<LoadState<List<AvailabilitySlot>>> = _slots.asStateFlow()

    private val _myBookings = MutableStateFlow<LoadState<List<ConsultationBooking>>>(LoadState.Loading)
    val myBookings: StateFlow<LoadState<List<ConsultationBooking>>> = _myBookings.asStateFlow()

    /** The booking currently being made or viewed — set once a slot is reserved, or when selecting an existing booking from My Consultations; carried through the payment/confirmation/cancel steps. */
    private val _activeBooking = MutableStateFlow<ConsultationBooking?>(null)
    val activeBooking: StateFlow<ConsultationBooking?> = _activeBooking.asStateFlow()

    init {
        loadExperts()
    }

    fun loadExperts() {
        _experts.value = LoadState.Loading
        viewModelScope.launch {
            _experts.value = try {
                LoadState.Success(repository.listActiveExperts())
            } catch (e: Exception) {
                LoadState.Error(e.message ?: "Couldn't load experts")
            }
        }
    }

    fun loadSlots(expertId: String) {
        _slots.value = LoadState.Loading
        viewModelScope.launch {
            _slots.value = try {
                LoadState.Success(repository.availableSlots(expertId))
            } catch (e: Exception) {
                LoadState.Error(e.message ?: "Couldn't load availability")
            }
        }
    }

    fun loadMyBookings(uiContext: Context) {
        _myBookings.value = LoadState.Loading
        authUiContext = uiContext
        viewModelScope.launch {
            try {
                _myBookings.value = LoadState.Success(repository.myBookings())
            } catch (e: Exception) {
                _myBookings.value = LoadState.Error(e.message ?: "Couldn't load your consultations")
            } finally {
                authUiContext = null
            }
        }
    }

    // reserveSlot's `vehicleContext` parameter isn't exposed here yet — no UI offers attaching
    // one yet (see ExpertRepository's doc). Add a `vehicleContext: JSONObject?` param here,
    // threaded through to the repository call, once that UI exists.
    fun reserveSlot(slotId: String, uiContext: Context, onResult: (Result<ConsultationBooking>) -> Unit) {
        authUiContext = uiContext
        viewModelScope.launch {
            try {
                val result = repository.reserveSlot(slotId)
                result.getOrNull()?.let {
                    _activeBooking.value = it
                    ConsultationNotifications.scheduleExpiryWarning(getApplication(), it.id, it.expertName, it.bookingReference)
                }
                onResult(result)
            } finally {
                authUiContext = null
            }
        }
    }

    /**
     * Phase 2/3 stand in for what Phase 4 makes real: the actual flow is
     * payment-provider checkout -> server-side signature verification -> this call only ever
     * reflecting a backend-confirmed state. Nothing here simulates a successful charge; it's
     * exercising the booking-state transition the real flow will drive. With the real
     * repository configured, this currently always fails — see
     * SupabaseExpertRepository.confirmBookingAfterPayment's doc — since there's no
     * `confirm_booking` backend action until Phase 4.
     */
    fun confirmBookingAfterPayment(bookingId: String, onResult: (Result<ConsultationBooking>) -> Unit) {
        viewModelScope.launch {
            val result = repository.confirmBookingAfterPayment(bookingId)
            result.getOrNull()?.let {
                _activeBooking.value = it
                ConsultationNotifications.cancelExpiryWarning(getApplication(), it.id)
            }
            onResult(result)
        }
    }

    fun cancelBooking(bookingId: String, uiContext: Context, onResult: (Result<Unit>) -> Unit) {
        authUiContext = uiContext
        viewModelScope.launch {
            try {
                val result = repository.cancelBooking(bookingId)
                if (result.isSuccess) {
                    ConsultationNotifications.cancelExpiryWarning(getApplication(), bookingId)
                    _activeBooking.value?.let {
                        ConsultationNotifications.showCancellationConfirmation(getApplication(), it.expertName, it.bookingReference)
                    }
                    _activeBooking.value = _activeBooking.value?.copy(status = BookingStatus.CANCELLED)
                    loadMyBookings(uiContext)
                }
                onResult(result)
            } finally {
                authUiContext = null
            }
        }
    }

    fun clearActiveBooking() {
        _activeBooking.value = null
    }

    /** Used by [com.enginesense.app.ui.screens.MyConsultationsScreen] when tapping an existing booking to view its details — reuses [activeBooking] as "the booking currently being viewed/acted on" rather than adding a second, near-identical state field. */
    fun selectBooking(booking: ConsultationBooking) {
        _activeBooking.value = booking
    }

    fun expertById(id: String): Expert? = (experts.value as? LoadState.Success)?.data?.firstOrNull { it.id == id }

    fun slotById(id: String): AvailabilitySlot? = (slots.value as? LoadState.Success)?.data?.firstOrNull { it.id == id }
}
