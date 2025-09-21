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

object BluetoothScanner {

    @SuppressLint("MissingPermission")
    suspend fun scan(context: Context, location: android.location.Location): List<BluetoothResult> {
        val results = mutableListOf<BluetoothResult>()

        val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val scanner: BluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner ?: return results

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("BluetoothScanner", "BLUETOOTH_SCAN not granted")
            return results
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val advertisedName = result.scanRecord?.deviceName
                val deviceName = advertisedName ?: result.device.name ?: result.device.address
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
                    val advertisedName = result.scanRecord?.deviceName
                    val deviceName = advertisedName ?: result.device.name ?: result.device.address
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

        // Wait 5 seconds while scanning
        delay(5000)

        scanner.stopScan(callback)
        Log.d("BluetoothScanner", "Scan complete: ${results.size} devices")

        return results
    }
}
