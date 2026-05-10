package com.ehazir.companion.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AttendanceEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(record: AttendanceEntity): Long

    @Query("SELECT * FROM attendance WHERE date = :date AND rfid = :rfid LIMIT 1")
    suspend fun findByDateAndRfid(date: String, rfid: String): AttendanceEntity?

    @Query("UPDATE attendance SET checkOut = :checkOut, isSynced = 0 WHERE id = :id")
    suspend fun updateCheckOut(id: Long, checkOut: String)

    @Query("SELECT * FROM attendance WHERE date = :date ORDER BY id DESC")
    fun getTodayRecords(date: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE isSynced = 0")
    suspend fun getUnsynced(): List<AttendanceEntity>

    @Query("UPDATE attendance SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM attendance WHERE isSynced = 0")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT * FROM attendance ORDER BY id DESC LIMIT 50")
    fun getRecentRecords(): Flow<List<AttendanceEntity>>
}
