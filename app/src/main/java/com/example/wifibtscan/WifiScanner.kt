package com.example.wifibtscan

import android.Manifest
import android.content.Context
import android.location.Location
import android.net.wifi.WifiManager
import androidx.annotation.RequiresPermission
import com.example.wifibtscan.model.WifiResult

object WifiScanner {
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun scan(context: Context, location: Location?): List<WifiResult> {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val results = wifiManager.scanResults
        return results.map {
            @Suppress("DEPRECATION")
            val ssid: String = it.SSID;
            WifiResult(
                ssid = ssid,
                bssid = it.BSSID,
                rssi = it.level,
                latitude = location?.latitude,
                longitude = location?.longitude
            )
        }
    }
}
