package com.ambay.billing.core.network

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AmbayApiService {

    @GET("v1/health")
    suspend fun checkHealth(
        @Query("nonce") nonce: String
    ): Response<ResponseBody>

    @POST("v1/pair/redeem")
    suspend fun pairRedeem(
        @Body body: RequestBody
    ): Response<ResponseBody>

    @POST("v1/sync/sales")
    suspend fun pushSales(
        @Body body: RequestBody
    ): Response<ResponseBody>

    @GET("v1/sync/sales")
    suspend fun pullSales(
        @Query("after") afterSeq: Long,
        @Query("limit") limit: Int = 200
    ): Response<ResponseBody>

    @POST("v1/devices/heartbeat")
    suspend fun heartbeat(
        @Body body: RequestBody
    ): Response<ResponseBody>

    @POST("v1/devices/{kid}/logout")
    suspend fun logoutDevice(
        @Path("kid") kid: String,
        @Body body: RequestBody
    ): Response<ResponseBody>

    @GET("v1/sync/items")
    suspend fun syncItems(
        @Query("after") afterSeq: Long
    ): Response<ResponseBody>
}
