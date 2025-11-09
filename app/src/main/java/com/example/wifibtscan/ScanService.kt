package com.example.wifibtscan

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.example.wifibtscan.model.BluetoothResult
import com.example.wifibtscan.model.WifiResult
import com.example.wifibtscan.network.BtThingSpeakApi
import com.example.wifibtscan.network.ThingSpeakApiClients
import com.example.wifibtscan.network.WifiThingSpeakApi
import kotlinx.coroutines.*
import java.util.Date

class ScanService : Service() {

    companion object {
        val wifiLiveData = MutableLiveData<List<WifiResult>>()
        val btLiveData = MutableLiveData<List<BluetoothResult>>()

        // Distance threshold (meters)
        private const val DISTANCE_THRESHOLD_METERS = 20f

        // Location check interval (ms)
        private const val CHECK_INTERVAL_MS = 10_000L

        private const val WIFI_API_KEY: String = BuildConfig.THINGSPEAK_WIFI_API_KEY
        private const val BT_API_KEY: String = BuildConfig.THINGSPEAK_BT_API_KEY

        private const val TAG = "ScanService"
        private const val CHANNEL_ID = "scan_channel"
        private const val CHANNEL_NAME = "WiFi+BT Scanning"
    }

    private val svcScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isScanning = false

    // Retrofit API instances
    private val wifiApi: WifiThingSpeakApi by lazy {
        ThingSpeakApiClients.wifiClient.create(WifiThingSpeakApi::class.java)
    }
    private val btApi: BtThingSpeakApi by lazy {
        ThingSpeakApiClients.btClient.create(BtThingSpeakApi::class.java)
    }

    // Last location where we performed a scan
    private var lastScanLocation: android.location.Location? = null

    // Device identifier to include with each upload (field7)
    private lateinit var deviceId: String

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())

        // compute deviceId (Settings.Secure.ANDROID_ID)
        deviceId = fetchDeviceId()

        // Initialize filter list, then start loop
        svcScope.launch {
            try {
                FilterManager.initOnce()
                Log.d(TAG, "Filter initialized: entries=${FilterManager.getFilteredCopy().size}")
            } catch (t: Throwable) {
                Log.e(TAG, "Filter init error", t)
            }
            startDistanceBasedScanLoop()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand startId=$startId")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("HardwareIds")
    private fun fetchDeviceId(): String {
        return try {
            val id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            if (id.isNullOrBlank()) "unknown-device" else id
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to read ANDROID_ID, using fallback", t)
            "unknown-device"
        }
    }

    private fun startDistanceBasedScanLoop() {
        svcScope.launch {
            while (isActive) {
                try {
                    val current = LocationHelper.getLastLocation(applicationContext)
                    if (current != null) {
                        val shouldScan = lastScanLocation == null ||
                                lastScanLocation!!.distanceTo(current) >= DISTANCE_THRESHOLD_METERS

                        if (shouldScan) {
                            lastScanLocation = current
                            performScan(current)
                        } else {
                            Log.d(TAG, "Not moved enough: moved=${lastScanLocation?.distanceTo(current) ?: 0f}m")
                        }
                    } else {
                        Log.d(TAG, "Current location is null — will retry")
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Distance loop error", t)
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun performScan(location: android.location.Location) {
        if (isScanning) {
            Log.d(TAG, "performScan skipped: already scanning")
            return
        }

        isScanning = true
        try {
            Log.d(TAG, "📍 performScan at ${location.latitude}, ${location.longitude}")

            // Post typed empty lists to indicate searching in UI
            wifiLiveData.postValue(emptyList())
            btLiveData.postValue(emptyList())

            // Run scans (safely)
            val rawWifi = try {
                WifiScanner.scan(applicationContext, location)
            } catch (t: Throwable) {
                Log.e(TAG, "WifiScanner.scan failed", t)
                emptyList()
            }

            val rawBt = try {
                BluetoothScanner.scan(applicationContext, location)
            } catch (t: Throwable) {
                Log.e(TAG, "BluetoothScanner.scan failed", t)
                emptyList()
            }

            // Filter
            val wifiResults = rawWifi.filter { wifi -> !FilterManager.isFiltered(wifi.bssid) }
            val btResults = rawBt.filter { bt -> !FilterManager.isFiltered(bt.address) }

            // Post filtered results to UI
            wifiLiveData.postValue(wifiResults)
            btLiveData.postValue(btResults)

            // Timestamp (ms)
            val timestampMillis = Date().time.toString()
            val lat = location.latitude
            val lon = location.longitude

            // Send each device to ThingSpeak (one POST per device with 1s delay)
            withContext(Dispatchers.IO) {
                // Wi-Fi devices
                for (wifi in wifiResults) {
                    try {
                        val call = wifiApi.postWifiData(
                            WIFI_API_KEY,
                            wifi.ssid ?: "",
                            wifi.bssid ?: "",
                            wifi.rssi,
                            timestampMillis,
                            lat,
                            lon,
                            deviceId
                        )
                        val resp = call.execute()
                        Log.d(TAG, "Wi-Fi sent: ${wifi.ssid} -> code=${resp.code()}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send Wi-Fi ${wifi.ssid}", e)
                    }
                    delay(1000)
                }

                // Bluetooth devices
                for (bt in btResults) {
                    try {
                        val call = btApi.postBluetoothData(
                            BT_API_KEY,
                            bt.name ?: "Unknown",
                            bt.address ?: "Unknown",
                            bt.rssi ?: 0,
                            timestampMillis,
                            lat,
                            lon,
                            deviceId
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

    // Notification helpers
    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        channel.description = "Foreground service for scanning Wi-Fi and Bluetooth"
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WiFi+BT Scanner")
            .setContentText("Wi-Fi & BT scanner running")
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
