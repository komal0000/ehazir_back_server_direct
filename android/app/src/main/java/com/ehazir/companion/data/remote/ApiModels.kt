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
data class StaffListRequest(
    @SerializedName("token") val token: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("provide_grade") val provideGrade: Boolean = true
)

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

// --- Today attendance dashboard ---
data class TodayAttendanceRequest(
    @SerializedName("token") val token: String,
    @SerializedName("device_id") val deviceId: String
)

data class TodayAttendanceResponse(
    @SerializedName("status") val status: Boolean = false,
    @SerializedName("data") val data: TodayAttendanceData? = null,
    @SerializedName("msg") val msg: List<String> = emptyList()
)

data class TodayAttendanceData(
    @SerializedName("student") val student: Map<String, Int> = emptyMap(),
    @SerializedName("staff") val staff: Map<String, Int> = emptyMap(),
    @SerializedName("teacher") val teacher: Map<String, Int> = emptyMap(),
    @SerializedName("grades") val grades: List<TodayAttendanceGradeDto> = emptyList()
)

data class TodayAttendanceGradeDto(
    @SerializedName("grade_id") val gradeId: Int,
    @SerializedName("grade_name") val gradeName: String,
    @SerializedName("sections") val sections: List<TodayAttendanceSectionDto> = emptyList()
)

data class TodayAttendanceSectionDto(
    @SerializedName("section_id") val sectionId: Int = 0,
    @SerializedName("section_name") val sectionName: String = "",
    @SerializedName("1") val status1: Int = 0,
    @SerializedName("2") val status2: Int = 0,
    @SerializedName("3") val status3: Int = 0,
    @SerializedName("4") val status4: Int = 0
)

// --- ZKTeco ADMS staff attendance ---
data class StaffAttendanceRecord(
    @SerializedName("userId") val userId: String,
    @SerializedName("punchTime") val punchTime: String,
    @SerializedName("inOutMode") val inOutMode: String? = null,
    @SerializedName("verifyMode") val verifyMode: String? = null
)

data class StaffAttendanceRequest(
    @SerializedName("records") val records: List<StaffAttendanceRecord>
)

data class StaffAttendanceResponse(
    @SerializedName("message") val message: String? = null,
    @SerializedName("status") val status: String? = null
)
