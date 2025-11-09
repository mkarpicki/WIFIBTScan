package com.example.wifibtscan.network

import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface BtThingSpeakApi {
    @FormUrlEncoded
    @POST("update")
    fun postBluetoothData(
        @Field("api_key") apiKey: String,
        @Field("field1") name: String?,
        @Field("field2") address: String?,
        @Field("field3") rssi: Int,
        @Field("field4") timestamp: String,
        @Field("field5") latitude: Double,
        @Field("field6") longitude: Double,
        @Field("field7") deviceId: String
    ): Call<Void>
}
