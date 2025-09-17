package com.example.wifibtscan.api

import com.example.wifibtscan.model.WifiResult
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface WifiApi {
    @POST("wifi")
    fun sendWifiResults(@Body results: List<WifiResult>): Call<Void>
}
