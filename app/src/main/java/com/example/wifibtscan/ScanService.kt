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
import kotlinx.coroutines.*
import com.example.wifibtscan.model.WifiResult
import com.example.wifibtscan.model.BluetoothResult

class ScanService : Service() {

    companion object {
        val wifiLiveData = MutableLiveData<List<WifiResult>>()
        val btLiveData = MutableLiveData<List<BluetoothResult>>()
        private const val TAG = "ScanService"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isScanning = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")
        createNotificationChannel()
        startForeground(1, buildNotification())
        startPeriodicScan()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand() startId=$startId")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPeriodicScan() {
        scope.launch {
            while (isActive) {
                if (!isScanning) {
                    isScanning = true
                    try {
                        val location = LocationHelper.getLastLocation(applicationContext)
                        location?.let {
                            Log.d(TAG, "📍 Location: ${it.latitude}, ${it.longitude}")

                            // Wi-Fi scan
                            val wifiResults = WifiScanner.scan(applicationContext, it)
                            Log.d(TAG, "Wi-Fi scan returned: ${wifiResults.size}")
                            wifiLiveData.postValue(wifiResults)

                            // Bluetooth scan
                            val btResults = BluetoothScanner.scan(applicationContext, it)
                            Log.d(TAG, "Bluetooth scan returned: ${btResults.size}")
                            btLiveData.postValue(btResults)
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Scan error", t)
                    } finally {
                        isScanning = false
                    }
                }
                delay(15000) // repeat every 15 seconds
            }
        }
    }

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
