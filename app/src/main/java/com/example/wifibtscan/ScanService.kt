package com.example.wifibtscan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.example.wifibtscan.model.BluetoothResult
import com.example.wifibtscan.model.WifiResult
import com.example.wifibtscan.network.BtThingSpeakApi
import com.example.wifibtscan.network.ThingSpeakApiClients
import com.example.wifibtscan.network.WifiThingSpeakApi
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import com.example.wifibtscan.BuildConfig

class ScanService : Service() {

    companion object {
        val wifiLiveData = MutableLiveData<List<WifiResult>>()
        val btLiveData = MutableLiveData<List<BluetoothResult>>()

        // Replace placeholders with your actual ThingSpeak write keys,
        // or switch to BuildConfig.* if you inject keys via Gradle.

        val WIFI_API_KEY = BuildConfig.THINGSPEAK_WIFI_API_KEY
        val BT_API_KEY = BuildConfig.THINGSPEAK_BT_API_KEY


        private const val TAG = "ScanService"
        private const val CHANNEL_ID = "scan_channel"
        private const val CHANNEL_NAME = "Scanning Service"
    }

    private val svcScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isScanning = false

    // Retrofit API instances (created from ThingSpeakApiClients)
    private val wifiApi: WifiThingSpeakApi by lazy {
        ThingSpeakApiClients.wifiClient.create(WifiThingSpeakApi::class.java)
    }
    private val btApi: BtThingSpeakApi by lazy {
        ThingSpeakApiClients.btClient.create(BtThingSpeakApi::class.java)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification("Scanning Wi-Fi & Bluetooth"))
        startScanLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand startId=$startId")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startScanLoop() {
        svcScope.launch {
            while (isActive) {
                try {
                    val location = LocationHelper.getLastLocation(applicationContext)
                    if (location != null) {
                        performScan(location)
                    } else {
                        Log.d(TAG, "No location available, skipping scan")
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Scan loop exception", t)
                }
                delay(10_000) // 10 seconds between scan attempts (adjustable)
            }
        }
    }

    private suspend fun performScan(location: android.location.Location) {
        if (isScanning) {
            Log.d(TAG, "Already scanning — skipping this iteration")
            return
        }

        isScanning = true
        try {
            Log.d(TAG, "📍 performScan at ${location.latitude}, ${location.longitude}")

            // Indicate refresh to UI by posting empty lists
            wifiLiveData.postValue(emptyList<WifiResult>())
            btLiveData.postValue(emptyList<BluetoothResult>())

            // Run scans (these are suspend functions in your scanners)
            val wifiResults = WifiScanner.scan(applicationContext, location)
            wifiLiveData.postValue(wifiResults)

            val btResults = BluetoothScanner.scan(applicationContext, location)
            btLiveData.postValue(btResults)

            // Prepare timestamp string for ThingSpeak field4
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val lat = location.latitude
            val lon = location.longitude

            // Send to ThingSpeak, one POST per device, respecting rate limits
            withContext(Dispatchers.IO) {
                // Wi-Fi devices
                for (wifi in wifiResults) {
                    try {
                        val call = wifiApi.postWifiData(
                            WIFI_API_KEY,
                            wifi.ssid ?: "",
                            wifi.bssid ?: "",
                            wifi.rssi,
                            timestamp,
                            lat,
                            lon
                        )
                        val resp = call.execute()
                        Log.d(TAG, "Wi-Fi sent: ${wifi.ssid} -> code=${resp.code()}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send Wi-Fi ${wifi.ssid}", e)
                    }
                    delay(1000) // 1s delay to be polite / avoid rate limits
                }

                // Bluetooth devices
                for (bt in btResults) {
                    try {
                        val call = btApi.postBluetoothData(
                            BT_API_KEY,
                            bt.name ?: "Unknown",
                            bt.address ?: "Unknown",
                            bt.rssi ?: 0,
                            timestamp,
                            lat,
                            lon
                        )
                        val resp = call.execute()
                        Log.d(TAG, "BT sent: ${bt.name ?: bt.address} -> code=${resp.code()}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send BT ${bt.name ?: bt.address}", e)
                    }
                    delay(1000)
                }
            }

        } catch (t: Throwable) {
            Log.e(TAG, "performScan error", t)
        } finally {
            isScanning = false
        }
    }

    // --- Notification helpers (inline, no external class required) ---
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            channel.description = "Foreground service for scanning Wi-Fi and Bluetooth"
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WiFi+BT Scanner")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        svcScope.cancel()
    }
}
