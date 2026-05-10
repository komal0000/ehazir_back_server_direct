package com.ehazir.companion.data.repository

import com.ehazir.companion.data.local.AttendanceDao
import com.ehazir.companion.data.local.AttendanceEntity
import com.ehazir.companion.data.preferences.AppPreferences
import com.ehazir.companion.data.remote.AttendanceRecord
import com.ehazir.companion.data.remote.EhazirApiService
import com.ehazir.companion.data.remote.SyncRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

@Singleton
class AttendanceRepository @Inject constructor(
    private val dao: AttendanceDao,
    private val prefs: AppPreferences,
    private val apiService: EhazirApiService
) {
    fun getTodayRecords(date: String): Flow<List<AttendanceEntity>> = dao.getTodayRecords(date)
    fun getRecentRecords(): Flow<List<AttendanceEntity>> = dao.getRecentRecords()
    fun getPendingCount(): Flow<Int> = dao.getPendingCount()

    suspend fun insertAttendance(record: AttendanceEntity): Long = dao.insert(record)

    suspend fun syncPendingRecords(): Result<Int> {
        return try {
            val unsynced = dao.getUnsynced()
            if (unsynced.isEmpty()) return Result.Success(0)

            val token    = prefs.localToken.first()
            val deviceId = prefs.deviceId.first()

            val datas = unsynced.map { record ->
                AttendanceRecord(
                    rfid     = record.rfid,
                    checkIn  = record.checkIn,
                    checkOut = record.checkOut,
                    date     = record.date
                )
            }

            val response = apiService.syncAttendance(
                SyncRequest(token = token, deviceId = deviceId, datas = datas)
            )

            if (response.isSuccessful) {
                dao.markSynced(unsynced.map { it.id })
                Result.Success(unsynced.size)
            } else {
                Result.Error("Sync failed: ${response.errorBody()?.string() ?: "Unknown error"}")
            }
        } catch (e: Exception) {
            Result.Error("Network error: ${e.message}")
        }
    }
}
