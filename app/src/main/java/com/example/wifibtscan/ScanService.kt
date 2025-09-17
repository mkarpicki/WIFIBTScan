package com.example.wifibtscan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.wifibtscan.api.ApiClient
import com.example.wifibtscan.api.WifiApi
import com.example.wifibtscan.api.BluetoothApi
import com.example.wifibtscan.model.WifiResult
import com.example.wifibtscan.model.BluetoothResult
import kotlinx.coroutines.*

class ScanService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        val wifiLiveData = MutableLiveData<List<WifiResult>>()
        val btLiveData = MutableLiveData<List<BluetoothResult>>()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())

        val wifiApi = ApiClient.retrofit.create(WifiApi::class.java)
        val btApi = ApiClient.retrofit.create(BluetoothApi::class.java)

        scope.launch {
            LocationHelper.listenForLocation(this@ScanService) { location ->
                launch {
                    val wifiResults: List<WifiResult> = WifiScanner.scan(this@ScanService, location)
                    val btResults: List<BluetoothResult> = BluetoothScanner.scan(location)

                    wifiLiveData.postValue(wifiResults)
                    btLiveData.postValue(btResults)

                    launch { wifiApi.sendWifiResults(wifiResults).execute() }
                    launch { btApi.sendBluetoothResults(btResults).execute() }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
