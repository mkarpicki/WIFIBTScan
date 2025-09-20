package com.example.wifibtscan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat

class MainActivity : ComponentActivity() {

    private lateinit var wifiListView: ListView
    private lateinit var btListView: ListView
    private lateinit var wifiAdapter: ArrayAdapter<String>
    private lateinit var btAdapter: ArrayAdapter<String>

    // Step 1: Foreground permissions
    private val foregroundPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.INTERNET
    )

    // Step 2: Background location
    private val backgroundPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION

    private val requestForegroundPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val granted = perms.values.all { it }
            if (granted) {
                // Foreground location granted → now request background location
                requestBackgroundPermission.launch(backgroundPermission)
            } else {
                Toast.makeText(this, "Foreground permissions required!", Toast.LENGTH_LONG).show()
            }
        }

    private val requestBackgroundPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startForegroundService(Intent(this, ScanService::class.java))
            } else {
                Toast.makeText(this, "Background location required!", Toast.LENGTH_LONG).show()
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

        ScanService.wifiLiveData.observe(this) { results ->
            wifiAdapter.clear()
            wifiAdapter.addAll(results.map { "${it.ssid} (${it.rssi} dBm)" })
            wifiAdapter.notifyDataSetChanged()
        }

        ScanService.btLiveData.observe(this) { results ->
            btAdapter.clear()
            btAdapter.addAll(results.map { "${it.name ?: "Unknown"} (${it.rssi} dBm)" })
            btAdapter.notifyDataSetChanged()
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val missingForeground = foregroundPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        val backgroundGranted =
            ActivityCompat.checkSelfPermission(this, backgroundPermission) == PackageManager.PERMISSION_GRANTED

        when {
            missingForeground.isNotEmpty() -> {
                // Step 1: request foreground permissions
                requestForegroundPermissions.launch(missingForeground.toTypedArray())
            }
            !backgroundGranted -> {
                // Step 2: request background location
                requestBackgroundPermission.launch(backgroundPermission)
            }
            else -> {
                // All granted
                startForegroundService(Intent(this, ScanService::class.java))
            }
        }
    }
}
