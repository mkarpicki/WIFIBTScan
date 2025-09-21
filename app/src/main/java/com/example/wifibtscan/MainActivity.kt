package com.example.wifibtscan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var wifiListView: ListView
    private lateinit var btListView: ListView

    private lateinit var wifiAdapter: ArrayAdapter<String>
    private lateinit var btAdapter: ArrayAdapter<String>

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private val TAG = "MainActivity"
    private val PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wifiListView = findViewById(R.id.wifiListView)
        btListView = findViewById(R.id.btListView)

        wifiAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        btAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())

        wifiListView.adapter = wifiAdapter
        btListView.adapter = btAdapter

        checkAndRequestPermissions()
        observeLiveData()
    }

    // ------------------- Permissions -------------------
    private fun checkAndRequestPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: $missing")
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            Log.d(TAG, "All permissions already granted")
            startScanService()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedPermissions = permissions.indices.filter { grantResults[it] != PackageManager.PERMISSION_GRANTED }
            if (deniedPermissions.isEmpty()) {
                startScanService()
            } else {
                val permanentlyDenied = deniedPermissions.filter {
                    !ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[it])
                }
                if (permanentlyDenied.isNotEmpty()) {
                    showPermissionSettingsDialog(permanentlyDenied.map { permissions[it] })
                } else {
                    Toast.makeText(
                        this,
                        "Permissions denied: ${deniedPermissions.map { permissions[it] }}",
                        Toast.LENGTH_LONG
                    ).show()
                    checkAndRequestPermissions()
                }
            }
        }
    }

    private fun showPermissionSettingsDialog(permanentlyDenied: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(
                "The app needs the following permissions to scan Wi-Fi and Bluetooth:\n\n" +
                        permanentlyDenied.joinToString("\n") +
                        "\n\nPlease enable them in Settings."
            )
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    // ------------------- Start Scan Service -------------------
    private fun startScanService() {
        Log.d(TAG, "Starting ScanService")
        val intent = Intent(this, ScanService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    // ------------------- Observe LiveData -------------------
    private fun observeLiveData() {
        ScanService.wifiLiveData.observe(this) { results ->
            Log.d(TAG, "Updating UI with ${results.size} Wi-Fi results")
            wifiAdapter.clear()
            wifiAdapter.addAll(results.map { "${it.ssid} (${it.rssi} dBm)" })
            wifiAdapter.notifyDataSetChanged()
        }

        ScanService.btLiveData.observe(this) { results ->
            Log.d(TAG, "Updating UI with ${results.size} BT results")
            btAdapter.clear()
            btAdapter.addAll(results.map { "${it.name ?: "Unknown"} (${it.rssi} dBm)" })
            btAdapter.notifyDataSetChanged()
        }
    }
}
