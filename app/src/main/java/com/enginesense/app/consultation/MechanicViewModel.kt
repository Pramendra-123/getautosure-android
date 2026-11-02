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

/** Whether the signed-in Google account can use Mechanic mode. */
sealed class MechanicAccess {
    object Checking : MechanicAccess()
    data class Ready(val profile: MechanicProfile) : MechanicAccess()
    /** Signed in, but this account isn't linked to a mechanic profile yet; [message] says which email to give the owner. */
    data class NotLinked(val message: String) : MechanicAccess()
    data class Failed(val message: String) : MechanicAccess()
    /** No Google account signed in to the app yet. */
    object NotSignedIn : MechanicAccess()
    /** Mechanic mode needs the live consultation service; the preview build has no mechanics. */
    object Unavailable : MechanicAccess()
}

/**
 * Backs Mechanic mode: a mechanic's own hours, leave and calls. Same sign-in handling as
 * [ConsultationViewModel] (a short-lived Activity context for Credential Manager, never held past
 * one call); the backend decides which mechanic is asking from that sign-in.
 */
class MechanicViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenCache = GoogleIdTokenCache()
    private var authUiContext: Context? = null

    private val repository: MechanicRepository? =
        if (ConsultationBackendConfig.isConfigured) {
            SupabaseExpertRepository(
                endpointUrl = ConsultationBackendConfig.ENDPOINT_URL,
                idTokenProvider = {
                    val uiContext = authUiContext
                        ?: throw ConsultationNotAuthenticatedException(IllegalStateException("no UI context available for sign-in"))
                    tokenCache.get(uiContext)
                }
            )
        } else {
            null
        }

    private val _access = MutableStateFlow<MechanicAccess>(MechanicAccess.Checking)
    val access: StateFlow<MechanicAccess> = _access.asStateFlow()

    private val _schedule = MutableStateFlow<LoadState<MechanicSchedule>>(LoadState.Loading)
    val schedule: StateFlow<LoadState<MechanicSchedule>> = _schedule.asStateFlow()

    private val _bookings = MutableStateFlow<LoadState<List<MechanicBooking>>>(LoadState.Loading)
    val bookings: StateFlow<LoadState<List<MechanicBooking>>> = _bookings.asStateFlow()

    /** Runs [block] with a sign-in context available, the same bounded way ConsultationViewModel does. */
    private fun withAuth(uiContext: Context, block: suspend (MechanicRepository) -> Unit) {
        val repo = repository ?: return
        authUiContext = uiContext
        viewModelScope.launch {
            try {
                block(repo)
            } finally {
                authUiContext = null
            }
        }
    }

    private fun friendly(e: Throwable, fallback: String): String =
        (e as? ConsultationHttpException)?.serverMessage?.takeIf { it.isNotBlank() } ?: fallback

    /** Checks the account, then loads schedule and calls if it's a mechanic. */
    fun load(uiContext: Context) {
        if (repository == null) {
            _access.value = MechanicAccess.Unavailable
            return
        }
        _access.value = MechanicAccess.Checking
        withAuth(uiContext) { repo ->
            try {
                _access.value = MechanicAccess.Ready(repo.me())
            } catch (e: Exception) {
                _access.value = if (e.isNotAMechanic()) {
                    MechanicAccess.NotLinked(friendly(e, "This Google account isn't linked to a mechanic profile yet."))
                } else if (e is ConsultationNotAuthenticatedException) {
                    MechanicAccess.NotSignedIn
                } else {
                    MechanicAccess.Failed(friendly(e, "Couldn't check your mechanic account. Check your connection and try again."))
                }
                return@withAuth
            }
            loadScheduleWith(repo)
            loadBookingsWith(repo)
        }
    }

    fun refreshBookings(uiContext: Context) = withAuth(uiContext) { loadBookingsWith(it) }

    private suspend fun loadScheduleWith(repo: MechanicRepository) {
        _schedule.value = LoadState.Loading
        _schedule.value = try {
            LoadState.Success(repo.schedule())
        } catch (e: Exception) {
            LoadState.Error(friendly(e, "Couldn't load your hours"))
        }
    }

    private suspend fun loadBookingsWith(repo: MechanicRepository) {
        _bookings.value = LoadState.Loading
        _bookings.value = try {
            LoadState.Success(repo.bookings())
        } catch (e: Exception) {
            LoadState.Error(friendly(e, "Couldn't load your calls"))
        }
    }

    /** Saves the whole weekly schedule. [onDone] gets null on success, or the message to show. */
    fun saveWeeklyHours(hours: List<WeeklyHours>, uiContext: Context, onDone: (String?) -> Unit) = withAuth(uiContext) { repo ->
        try {
            _schedule.value = LoadState.Success(repo.setWeeklyHours(hours))
            onDone(null)
        } catch (e: Exception) {
            onDone(friendly(e, "Couldn't save your hours"))
        }
    }

    fun addLeave(startDate: String, endDate: String, uiContext: Context, onDone: (String?) -> Unit) = withAuth(uiContext) { repo ->
        try {
            _schedule.value = LoadState.Success(repo.addLeave(startDate, endDate))
            onDone(null)
        } catch (e: Exception) {
            onDone(friendly(e, "Couldn't save your leave"))
        }
    }

    fun removeLeave(leaveId: String, uiContext: Context, onDone: (String?) -> Unit) = withAuth(uiContext) { repo ->
        try {
            _schedule.value = LoadState.Success(repo.removeLeave(leaveId))
            onDone(null)
        } catch (e: Exception) {
            onDone(friendly(e, "Couldn't remove that leave"))
        }
    }

    fun requestJoinToken(bookingId: String, uiContext: Context, onResult: (Result<String>) -> Unit) = withAuth(uiContext) { repo ->
        onResult(
            try {
                Result.success(repo.mechanicJoinToken(bookingId))
            } catch (e: Exception) {
                Result.failure(IllegalStateException(friendly(e, "Couldn't join the call"), e))
            }
        )
    }
}
