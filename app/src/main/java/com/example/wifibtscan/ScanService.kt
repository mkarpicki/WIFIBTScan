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
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat

class ScanService : Service() {

    companion object {
        val wifiLiveData = MutableLiveData<List<WifiResult>>()
        val btLiveData = MutableLiveData<List<BluetoothResult>>()

        // Change this constant to adjust the distance threshold (meters)
        private const val DISTANCE_THRESHOLD_METERS = 20f

        // How often we check location for movement (ms)
        private const val CHECK_INTERVAL_MS = 10_000L

        // ThingSpeak keys (injected via BuildConfig)
        private val WIFI_API_KEY: String = BuildConfig.THINGSPEAK_WIFI_API_KEY
        private val BT_API_KEY: String = BuildConfig.THINGSPEAK_BT_API_KEY

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

    // Last location where we performed a scan (null until first scan)
    private var lastScanLocation: android.location.Location? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification("Wi-Fi & BT scanner running"))

        // Initialize filter list once, then start distance-based loop
        svcScope.launch {
            try {
                FilterManager.initOnce()
                Log.d(TAG, "Filter initialized, entries=${FilterManager.getFilteredCopy().size}")
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

    private fun startDistanceBasedScanLoop() {
        svcScope.launch {
            while (isActive) {
                try {
                    // getLastLocation is a suspend function in your LocationHelper
                    val current = LocationHelper.getLastLocation(applicationContext)
                    if (current != null) {
                        val shouldScan = lastScanLocation == null ||
                                lastScanLocation!!.distanceTo(current) >= DISTANCE_THRESHOLD_METERS

                        if (shouldScan) {
                            // store current as "last" before scanning to avoid race conditions
                            lastScanLocation = current
                            performScan(current)
                        } else {
                            Log.d(TAG, "Not moved enough: need $DISTANCE_THRESHOLD_METERS m (moved ${lastScanLocation?.distanceTo(current) ?: 0f})")
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

            // Indicate UI refresh by posting typed empty lists
            wifiLiveData.postValue(emptyList<WifiResult>())
            btLiveData.postValue(emptyList<BluetoothResult>())

            // Run scans (your WifiScanner / BluetoothScanner are suspend functions)
            val rawWifi = try {
                WifiScanner.scan(applicationContext, location)
            } catch (t: Throwable) {
                Log.e(TAG, "WifiScanner.scan failed", t)
                emptyList<WifiResult>()
            }

            val rawBt = try {
                BluetoothScanner.scan(applicationContext, location)
            } catch (t: Throwable) {
                Log.e(TAG, "BluetoothScanner.scan failed", t)
                emptyList<BluetoothResult>()
            }

            // Filter out addresses present in the FilterManager
            val wifiResults = rawWifi.filter { wifi -> !FilterManager.isFiltered(wifi.bssid) }
            val btResults = rawBt.filter { bt -> !FilterManager.isFiltered(bt.address) }

            // Post filtered lists to UI
            wifiLiveData.postValue(wifiResults)
            btLiveData.postValue(btResults)

            // Prepare timestamp (milliseconds since epoch)
            val timestampMillis = Date().time.toString()
            val lat = location.latitude
            val lon = location.longitude

            // Send each device to ThingSpeak (1s delay between posts)
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
                            lon
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

    // --- Notification helpers ---
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
