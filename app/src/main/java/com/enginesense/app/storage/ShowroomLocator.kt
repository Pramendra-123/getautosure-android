package com.enginesense.app.storage

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Where the dealer is standing, as a map point and a short address for the report. */
data class ShowroomLocation(val latitude: Double, val longitude: Double, val address: String?)

/**
 * "Use my location" on the dealer profile. Uses the platform LocationManager (no Play Services
 * dependency) with coarse accuracy — a showroom address doesn't need GPS precision. The caller
 * must already hold ACCESS_COARSE_LOCATION; returns null when no fix comes within a few seconds.
 */
object ShowroomLocator {

    @SuppressLint("MissingPermission")
    suspend fun locate(context: Context): ShowroomLocation? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return null

        val fix = withTimeoutOrNull(8_000) { currentLocation(context, manager, providers.first()) }
            ?: providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
            ?: return null

        val address = withContext(Dispatchers.IO) { addressFor(context, fix.latitude, fix.longitude) }
        return ShowroomLocation(fix.latitude, fix.longitude, address)
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context, manager: LocationManager, provider: String): Location? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            suspendCancellableCoroutine { cont ->
                manager.getCurrentLocation(provider, null, context.mainExecutor) { cont.resume(it) }
            }
        } else {
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }

    /** "14, Hosur Road, Koramangala, Bengaluru" — the street-to-city part of the first match, or null offline. */
    @Suppress("DEPRECATION")
    private fun addressFor(context: Context, lat: Double, lng: Double): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        val a = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)?.firstOrNull() ?: return null
        listOfNotNull(a.subThoroughfare, a.thoroughfare, a.subLocality, a.locality)
            .distinct().joinToString(", ").ifBlank { a.getAddressLine(0) }
    }.getOrNull()
}
