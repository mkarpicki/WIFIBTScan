package com.example.wifibtscan.model

data class WifiResult(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val latitude: Double?,
    val longitude: Double?
)
