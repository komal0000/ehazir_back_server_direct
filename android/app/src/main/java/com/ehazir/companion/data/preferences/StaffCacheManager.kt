package com.ehazir.companion.data.preferences

import android.content.Context
import com.ehazir.companion.data.remote.StaffListData
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StaffCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val cacheFile: File
        get() = File(context.filesDir, "staff_list_cache.json")

    fun save(data: StaffListData) {
        cacheFile.writeText(gson.toJson(data))
    }

    fun load(): StaffListData? {
        return try {
            if (cacheFile.exists()) {
                gson.fromJson(cacheFile.readText(), StaffListData::class.java)
            } else {
                null
            }
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    fun exists(): Boolean = cacheFile.exists()
}
