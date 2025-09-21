package com.example.wifibtscan

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.wifibtscan.model.WifiResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object WifiScanner {

    @SuppressLint("MissingPermission")
    suspend fun scan(context: Context, location: android.location.Location): List<WifiResult> =
        suspendCancellableCoroutine { cont ->

            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("WifiScanner", "ACCESS_FINE_LOCATION not granted")
                cont.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val results = wifiManager.scanResults.map {
                        WifiResult(
                            ssid = @Suppress("DEPRECATION") it.SSID,
                            bssid = it.BSSID,
                            rssi = it.level,
                            latitude = location.latitude,
                            longitude = location.longitude
                        )
                    }
                    Log.d("WifiScanner", "BroadcastReceiver: ${results.size} Wi-Fi networks")
                    context.unregisterReceiver(this)
                    cont.resume(results)
                }
            }

            context.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
            val started = wifiManager.startScan()
            Log.d("WifiScanner", "startScan returned: $started")
        }
}
