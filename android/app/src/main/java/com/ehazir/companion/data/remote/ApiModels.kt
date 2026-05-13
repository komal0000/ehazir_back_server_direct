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

// --- Staff list ---
data class StaffListResponse(
    @SerializedName("status") val status: Boolean = false,
    @SerializedName("data") val data: StaffListData? = null,
    @SerializedName("msg") val msg: String? = null
)

data class StaffListData(
    @SerializedName("staffs") val staffs: List<StaffDto> = emptyList(),
    @SerializedName("grades") val grades: List<GradeDto> = emptyList()
)

data class StaffDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("sections") val sections: List<StaffSectionDto> = emptyList()
)

data class StaffSectionDto(
    @SerializedName("section_id") val sectionId: Int,
    @SerializedName("section_name") val sectionName: String,
    @SerializedName("grade_id") val gradeId: Int,
    @SerializedName("grade_name") val gradeName: String
)

data class GradeDto(
    @SerializedName("id") val id: Int,
    @SerializedName("grade") val grade: String,
    @SerializedName("sections") val sections: List<GradeSectionDto> = emptyList()
)

data class GradeSectionDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String
)
