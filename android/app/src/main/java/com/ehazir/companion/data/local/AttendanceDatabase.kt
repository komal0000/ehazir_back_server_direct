package com.ehazir.companion.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AttendanceEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AttendanceDatabase : RoomDatabase() {
    abstract fun attendanceDao(): AttendanceDao

    companion object {
        const val DATABASE_NAME = "ehazir_attendance.db"
    }
}
