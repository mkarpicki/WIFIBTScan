package com.example.wifibtscan

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.wifibtscan.model.BluetoothResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object BluetoothScanner {

    @SuppressLint("MissingPermission")
    suspend fun scan(context: Context, location: android.location.Location): List<BluetoothResult> =
        suspendCancellableCoroutine { cont ->

            val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            val scanner: BluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
                cont.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("BluetoothScanner", "BLUETOOTH_SCAN not granted")
                cont.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            val results = mutableListOf<BluetoothResult>()

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val deviceName = result.device.name ?: "Unknown"
                    Log.d("BluetoothScanner", "Found device: $deviceName")
                    results.add(
                        BluetoothResult(
                            name = deviceName,
                            address = result.device.address,
                            rssi = result.rssi,
                            latitude = location.latitude,
                            longitude = location.longitude
                        )
                    )
                }

                override fun onBatchScanResults(batchResults: MutableList<ScanResult>) {
                    batchResults.forEach { result ->
                        val deviceName = result.device.name ?: "Unknown"
                        results.add(
                            BluetoothResult(
                                name = deviceName,
                                address = result.device.address,
                                rssi = result.rssi,
                                latitude = location.latitude,
                                longitude = location.longitude
                            )
                        )
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e("BluetoothScanner", "Scan failed: $errorCode")
                }
            }

            scanner.startScan(callback)
            // Wait 5 seconds for scan
            context.mainExecutor.execute {
                suspend {
                    delay(5000)
                    scanner.stopScan(callback)
                    Log.d("BluetoothScanner", "Scan complete: ${results.size} devices")
                    cont.resume(results)
                }
            }
        }
}
