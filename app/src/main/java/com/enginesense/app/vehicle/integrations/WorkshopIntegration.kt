package com.enginesense.app.vehicle.integrations

import com.enginesense.app.vehicle.DiagnosticFinding

data class WorkshopSuggestion(
    val workshopId: String,
    val name: String,
    val distanceKm: Double?,
    val rating: Double?,
    val canHandle: List<String>
)

sealed interface BookingResult {
    data class Confirmed(val bookingId: String, val workshopName: String, val etaText: String) : BookingResult
    data class Failed(val reason: String) : BookingResult
}

/**
 * Interface for connecting a Car Health Report's findings to a real workshop/mechanic
 * network (the "Take Action" step). No real network integration exists yet — this is a
 * placeholder so the UI layer (stage 10) has something stable to call once a workshop
 * partner (or GetAutosure's own dealer/workshop network) is wired in.
 */
interface WorkshopBookingService {
    suspend fun findNearbyWorkshops(findings: List<DiagnosticFinding>, locationHint: String?): List<WorkshopSuggestion>
    suspend fun requestBooking(workshopId: String, findings: List<DiagnosticFinding>): BookingResult
}

/**
 * Placeholder implementation: returns a clearly-labeled "not yet integrated" result
 * rather than fabricating realistic-looking workshop data. Swap for a real
 * implementation once a workshop/mechanic partner or booking API is chosen.
 */
class PlaceholderWorkshopBookingService : WorkshopBookingService {

    override suspend fun findNearbyWorkshops(
        findings: List<DiagnosticFinding>,
        locationHint: String?
    ): List<WorkshopSuggestion> {
        return listOf(
            WorkshopSuggestion(
                workshopId = "placeholder",
                name = "Workshop network not yet connected",
                distanceKm = null,
                rating = null,
                canHandle = findings.map { it.title }
            )
        )
    }

    override suspend fun requestBooking(workshopId: String, findings: List<DiagnosticFinding>): BookingResult {
        return BookingResult.Failed("Workshop booking isn't wired up yet — this is a placeholder.")
    }
}
