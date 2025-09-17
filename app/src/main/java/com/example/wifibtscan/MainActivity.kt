package com.example.wifibtscan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer

class MainActivity : ComponentActivity() {

    private lateinit var wifiListView: ListView
    private lateinit var btListView: ListView
    private lateinit var wifiAdapter: ArrayAdapter<String>
    private lateinit var btAdapter: ArrayAdapter<String>

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val granted = perms.values.all { it }
            if (granted) {
                startForegroundService(Intent(this, ScanService::class.java))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wifiListView = findViewById(R.id.wifiListView)
        btListView = findViewById(R.id.btListView)

        wifiAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        btAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())

        wifiListView.adapter = wifiAdapter
        btListView.adapter = btAdapter

        // Observe LiveData from service
        ScanService.wifiLiveData.observe(this, Observer { results ->
            wifiAdapter.clear()
            wifiAdapter.addAll(results.map { "${it.ssid} (${it.rssi} dBm)" })
            wifiAdapter.notifyDataSetChanged()
        })

        ScanService.btLiveData.observe(this, Observer { results ->
            btAdapter.clear()
            btAdapter.addAll(results.map { "${it.name ?: "Unknown"} (${it.rssi} dBm)" })
            btAdapter.notifyDataSetChanged()
        })

        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.INTERNET
        )

        if (permissions.all {
                ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
            startForegroundService(Intent(this, ScanService::class.java))
        } else {
            requestPermissions.launch(permissions)
        }
    }
}
