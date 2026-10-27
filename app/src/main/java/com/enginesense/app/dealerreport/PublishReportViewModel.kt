package com.enginesense.app.dealerreport

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enginesense.app.auth.GoogleIdTokenCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

sealed class PublishState {
    data object Idle : PublishState()
    data object Publishing : PublishState()
    data class Success(val report: PublishedReport, val unpublished: Boolean = false) : PublishState()
    data class Error(val message: String) : PublishState()
}

/**
 * Backs [com.enginesense.app.ui.screens.PublishReportScreen] — same real/not-configured gating
 * and bounded [authUiContext] lifetime as
 * [com.enginesense.app.consultation.ConsultationViewModel], see that class's doc. No mock
 * fallback here (unlike Consultation's MockExpertRepository) — a one-shot publish-with-photos
 * action has nothing meaningful to simulate; when the backend isn't deployed yet, this just says
 * so plainly.
 */
class PublishReportViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenCache = GoogleIdTokenCache()

    private val repository: DealerReportRepository? =
        if (DealerReportBackendConfig.isConfigured) {
            DealerReportRepository(
                endpointUrl = DealerReportBackendConfig.ENDPOINT_URL,
                idTokenProvider = {
                    val uiContext = authUiContext
                        ?: throw DealerReportNotAuthenticatedException(IllegalStateException("no UI context available for sign-in"))
                    tokenCache.get(uiContext)
                }
            )
        } else {
            null
        }

    private var authUiContext: Context? = null
    // Several removeBackground calls can be in flight together (one per exterior slot), each
    // setting/clearing authUiContext around its own idTokenProvider() call — a plain null-on-finally
    // would let one call's cleanup yank the context out from under another still in progress.
    private var authUiContextUsers = 0

    private val _publishState = MutableStateFlow<PublishState>(PublishState.Idle)
    val publishState: StateFlow<PublishState> = _publishState.asStateFlow()

    fun publish(
        uiContext: Context,
        dealerName: String,
        dealerTagline: String,
        dealerPhone: String,
        dealerLocation: String,
        dealerLogo: PhotoUpload?,
        make: String?,
        model: String?,
        variant: String?,
        year: Int?,
        fuelType: String?,
        transmission: String?,
        odometerKm: Int?,
        ownership: String?,
        registrationNo: String?,
        healthSummary: JSONObject,
        dealerCondition: DealerConditionInput,
        photos: List<PhotoUpload>,
        scannedAtIso: String?
    ) {
        val repo = repository
        if (repo == null) {
            _publishState.value = PublishState.Error("Web report publishing isn't live yet — the backend hasn't been deployed.")
            return
        }
        if (dealerName.isBlank()) {
            _publishState.value = PublishState.Error("Set up your dealer profile first (name is required on a published report).")
            return
        }

        _publishState.value = PublishState.Publishing
        viewModelScope.launch {
            withAuthContext(uiContext) {
                val result = repo.publishReport(
                    dealerName, dealerTagline, dealerPhone, dealerLocation, dealerLogo,
                    make, model, variant, year, fuelType, transmission, odometerKm, ownership, registrationNo,
                    healthSummary, dealerCondition, photos, scannedAtIso
                )
                _publishState.value = result.fold(
                    onSuccess = { PublishState.Success(it) },
                    onFailure = { PublishState.Error(it.message ?: "Couldn't publish this report") }
                )
            }
        }
    }

    fun reset() {
        _publishState.value = PublishState.Idle
    }

    /** Per-photo preview, independent of [publishState] — several exterior slots can be processing at once, each tracked by the screen itself. */
    fun removeBackground(uiContext: Context, photo: PhotoUpload, onResult: (Result<PhotoUpload>) -> Unit) {
        val repo = repository
        if (repo == null) {
            onResult(Result.failure(DealerReportNotAuthenticatedException(IllegalStateException("backend not configured"))))
            return
        }
        viewModelScope.launch {
            withAuthContext(uiContext) {
                onResult(repo.removeBackground(photo))
            }
        }
    }

    fun unpublish(uiContext: Context, report: PublishedReport) {
        val repo = repository ?: return
        viewModelScope.launch {
            withAuthContext(uiContext) {
                val result = repo.updateReportStatus(report.slug, "unpublished")
                _publishState.value = result.fold(
                    onSuccess = { PublishState.Success(report, unpublished = true) },
                    onFailure = { PublishState.Error(it.message ?: "Couldn't unpublish this report") }
                )
            }
        }
    }

    /** Ref-counted so concurrent calls (e.g. several exterior slots processing at once) don't clear authUiContext out from under one another. */
    private suspend fun withAuthContext(uiContext: Context, block: suspend () -> Unit) {
        authUiContext = uiContext
        authUiContextUsers++
        try {
            block()
        } finally {
            authUiContextUsers--
            if (authUiContextUsers == 0) authUiContext = null
        }
    }
}
