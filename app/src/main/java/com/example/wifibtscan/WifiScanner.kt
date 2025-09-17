package com.example.wifibtscan

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.core.app.ActivityCompat
import com.example.wifibtscan.model.WifiResult

object WifiScanner {

    @SuppressLint("MissingPermission") // suppress lint warning safely
    fun scan(context: Context, location: android.location.Location): List<WifiResult> {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // ✅ Runtime permission check
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        return wifiManager.scanResults.map {
            WifiResult(
                ssid = @Suppress("DEPRECATION") it.SSID,
                bssid = it.BSSID,
                rssi = it.level,
                latitude = location.latitude,
                longitude = location.longitude
            )
        }
    }
}
