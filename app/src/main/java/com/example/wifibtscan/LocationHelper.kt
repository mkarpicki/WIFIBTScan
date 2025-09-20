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

    private var checkForPositionUpdate = 10000L; //10s
    private var numberOfMetersToReact = 0.5f; //30f

    @SuppressLint("MissingPermission")
    fun listenForLocation(context: Context, onUpdate: (android.location.Location) -> Unit) {
        if (fusedLocationClient == null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, checkForPositionUpdate // every 10s
        ).setMinUpdateDistanceMeters(numberOfMetersToReact) // retrigger if moved > 30m
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
