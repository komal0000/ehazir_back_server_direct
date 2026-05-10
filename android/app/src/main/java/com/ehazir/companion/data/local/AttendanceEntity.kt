package com.ehazir.companion.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance",
    indices = [Index(value = ["date", "rfid"], unique = true)]
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rfid: String,         // student RFID / ID number
    val date: String,         // YYYY-MM-DD  (auto-captured)
    val checkIn: String,      // HH:mm:ss   (auto-captured on Submit)
    val checkOut: String,     // HH:mm:ss   (defaults to checkIn, optionally overridden)
    val isSynced: Int = 0     // 0=pending, 1=synced
)
