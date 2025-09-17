package com.example.wifibtscan.api

import com.example.wifibtscan.model.BluetoothResult
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface BluetoothApi {
    @POST("bluetooth")
    fun sendBluetoothResults(@Body results: List<BluetoothResult>): Call<Void>
}
