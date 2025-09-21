package com.example.wifibtscan

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

object LocationHelper {

    private const val TAG = "LocationHelper"

    /**
     * Returns the last known location as a suspend function.
     * Returns null if location is unavailable or permission is missing.
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(context: Context): Location? {
        return try {
            val fusedClient: FusedLocationProviderClient =
                LocationServices.getFusedLocationProviderClient(context)

            val location = fusedClient.lastLocation.await()
            if (location != null) {
                Log.d(TAG, "Last location: ${location.latitude}, ${location.longitude}")
            } else {
                Log.d(TAG, "No last location available")
            }
            location
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get last location", e)
            null
        }
    }
}
