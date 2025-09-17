package com.example.wifibtscan.model

data class BluetoothResult(
    val name: String?,
    val address: String,
    val rssi: Int,
    val latitude: Double?,
    val longitude: Double?
)
