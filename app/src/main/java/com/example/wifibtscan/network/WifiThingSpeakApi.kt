package com.example.wifibtscan.network

import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface WifiThingSpeakApi {
    @FormUrlEncoded
    @POST("update")
    fun postWifiData(
        @Field("api_key") apiKey: String,
        @Field("field1") ssid: String,
        @Field("field2") bssid: String,
        @Field("field3") rssi: Int,
        @Field("field4") timestamp: String,
        @Field("field5") latitude: Double,
        @Field("field6") longitude: Double,
        @Field("field7") deviceId: String
    ): Call<Void>
}
