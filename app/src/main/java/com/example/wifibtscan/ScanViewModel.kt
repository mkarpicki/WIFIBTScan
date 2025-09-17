package com.example.wifibtscan

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.wifibtscan.model.WifiResult
import com.example.wifibtscan.model.BluetoothResult

class ScanViewModel : ViewModel() {
    private val _wifiResults = MutableLiveData<List<WifiResult>>()
    val wifiResults: LiveData<List<WifiResult>> = _wifiResults

    private val _btResults = MutableLiveData<List<BluetoothResult>>()
    val btResults: LiveData<List<BluetoothResult>> = _btResults

    fun updateWifi(results: List<WifiResult>) {
        _wifiResults.postValue(results)
    }

    fun updateBt(results: List<BluetoothResult>) {
        _btResults.postValue(results)
    }
}
