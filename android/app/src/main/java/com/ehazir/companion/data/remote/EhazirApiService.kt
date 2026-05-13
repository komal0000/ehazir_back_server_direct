package com.ehazir.companion.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface EhazirApiService {

    @POST("api/device/register")
    suspend fun registerDevice(
        @Body request: RegisterRequest
    ): Response<RegisterResponse>

    @POST("api/device/set")
    suspend fun syncAttendance(
        @Body request: SyncRequest
    ): Response<SyncResponse>

    @GET("api/device/staff-list")
    suspend fun getStaffList(
        @Query("provide_grade") provideGrade: Boolean = true
    ): Response<StaffListResponse>
}

interface ValidationApiService {
    @GET("ask.php?ask=token")
    suspend fun getTokenPrompt(): Response<String>
}
