package com.example.wifibtscan

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import com.example.wifibtscan.model.BluetoothResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object BluetoothScanner {
    suspend fun scan(location: android.location.Location?): List<BluetoothResult> =
        suspendCancellableCoroutine { cont ->
            val adapter = BluetoothAdapter.getDefaultAdapter()
            val scanner = adapter.bluetoothLeScanner
            val devices = mutableListOf<BluetoothResult>()

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.device?.let {
                        devices.add(
                            BluetoothResult(
                                name = it.name,
                                address = it.address,
                                rssi = result.rssi,
                                latitude = location?.latitude,
                                longitude = location?.longitude
                            )
                        )
                    }
                }
            }

            scanner.startScan(callback)

            // stop after short delay
            cont.invokeOnCancellation { scanner.stopScan(callback) }
            Thread {
                Thread.sleep(3000)
                scanner.stopScan(callback)
                cont.resume(devices)
            }.start()
        }
}
