package com.ehazir.companion.data.remote

import com.google.gson.annotations.SerializedName

// --- Registration ---
data class RegisterRequest(
    @SerializedName("token") val token: String,
    @SerializedName("local_token") val localToken: String,
    @SerializedName("device_id") val deviceId: String
)

data class RegisterResponse(
    @SerializedName("message") val message: String? = null,
    @SerializedName("status") val status: String? = null
)

// --- Sync attendance ---
// Matches main.js payload: { rfid, in_time, out_time, date }
data class AttendanceRecord(
    @SerializedName("rfid")      val rfid: String,
    @SerializedName("in_time")   val checkIn: String,
    @SerializedName("out_time")  val checkOut: String,
    @SerializedName("date")      val date: String
)

data class SyncRequest(
    @SerializedName("token")     val token: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("datas")     val datas: List<AttendanceRecord>
)

data class SyncResponse(
    @SerializedName("message") val message: String? = null,
    @SerializedName("status")  val status: String? = null
)
