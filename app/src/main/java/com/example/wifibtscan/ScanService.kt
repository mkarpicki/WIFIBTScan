package com.example.wifibtscan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.core.app.NotificationCompat
import com.example.wifibtscan.model.WifiResult
import com.example.wifibtscan.model.BluetoothResult
import kotlinx.coroutines.*

class ScanService : Service() {

    companion object {
        val wifiLiveData = MutableLiveData<List<WifiResult>>()
        val btLiveData = MutableLiveData<List<BluetoothResult>>()
        private const val TAG = "ScanService"

        private const val THRESHOLD_METERS = 1f // 20f // scan only if device moves >= 20 meters
        private const val DELAY_MILIS = 5000
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastLocation: android.location.Location? = null
    private var isScanning = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")
        createNotificationChannel()
        startForeground(1, buildNotification())
        startDistanceBasedScanLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand() startId=$startId")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------- Distance-based scanning --------------------
    private fun startDistanceBasedScanLoop() {
        scope.launch {
            while (isActive) {
                try {
                    val currentLocation = LocationHelper.getLastLocation(applicationContext)
                    currentLocation?.let { location ->
                        val shouldScan = lastLocation == null ||
                                lastLocation!!.distanceTo(location) >= THRESHOLD_METERS
                        if (shouldScan && !isScanning) {
                            lastLocation = location
                            performScan(location)
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Scan loop error", t)
                }
                delay(DELAY_MILIS.toLong()) // check location every 5 seconds
            }
        }
    }

    private suspend fun performScan(location: android.location.Location) {
        isScanning = true
        try {
            Log.d(TAG, "📍 Scanning at location: ${location.latitude}, ${location.longitude}")

            // Wi-Fi scan
            val wifiResults: List<WifiResult> = WifiScanner.scan(applicationContext, location)
            Log.d(TAG, "Wi-Fi scan returned: ${wifiResults.size}")
            wifiLiveData.postValue(wifiResults)

            // Bluetooth scan
            val btResults: List<BluetoothResult> = BluetoothScanner.scan(applicationContext, location)
            Log.d(TAG, "Bluetooth scan returned: ${btResults.size}")
            btLiveData.postValue(btResults)

        } catch (t: Throwable) {
            Log.e(TAG, "Scan error", t)
        } finally {
            isScanning = false
        }
    }

    // -------------------- Notification --------------------
    private fun createNotificationChannel() {
        val channel = NotificationChannel("scan_channel", "Scan Service", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, "scan_channel")
            .setContentTitle("Scanning Wi-Fi & Bluetooth")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
