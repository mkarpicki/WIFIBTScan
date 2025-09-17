package com.example.wifibtscan

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object LocationHelper {
    private var fusedLocationClient: FusedLocationProviderClient? = null

    @SuppressLint("MissingPermission")
    fun listenForLocation(context: Context, onUpdate: (android.location.Location) -> Unit) {
        if (fusedLocationClient == null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 10000L // every 10s
        ).setMinUpdateDistanceMeters(30f) // retrigger if moved > 30m
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                CoroutineScope(Dispatchers.Default).launch {
                    locationResult.locations.lastOrNull()?.let { onUpdate(it) }
                }
            }
        }

        fusedLocationClient?.requestLocationUpdates(
            request,
            callback,
            Looper.getMainLooper()
        )
    }
}
