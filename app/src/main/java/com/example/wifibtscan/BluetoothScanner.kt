package com.example.wifibtscan

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult as BtScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.wifibtscan.model.BluetoothResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object BluetoothScanner {

    suspend fun scan(context: Context, location: android.location.Location): List<BluetoothResult> {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return emptyList()

        return suspendCancellableCoroutine { cont ->
            val results = mutableListOf<BluetoothResult>()

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: BtScanResult) {
                    val deviceName: String? = if (
                        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        result.device.name
                    } else {
                        null
                    }

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

                override fun onScanFailed(errorCode: Int) {
                    Log.e("BluetoothScanner", "Scan failed with error: $errorCode")
                    cont.resume(emptyList())
                }
            }

            // ✅ Permission check before scanning
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED
            ) {
                scanner.startScan(callback)
            } else {
                Log.w("BluetoothScanner", "BLUETOOTH_SCAN permission not granted")
                cont.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            // Stop scan after 5s
            android.os.Handler().postDelayed({
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    scanner.stopScan(callback)
                }
                if (cont.isActive) cont.resume(results)
            }, 5000)
        }
    }
}
