package com.ehazir.companion.di

import android.content.Context
import androidx.room.Room
import com.ehazir.companion.data.local.AttendanceDao
import com.ehazir.companion.data.local.AttendanceDatabase
import com.ehazir.companion.data.preferences.AppPreferences
import com.ehazir.companion.data.remote.EhazirApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Interceptor that reads the current server URL from DataStore at every request,
 * so changing Settings takes effect immediately without restarting.
 */
class DynamicUrlInterceptor(private val prefs: AppPreferences) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val baseUrl = runBlocking {
            val url = prefs.serverUrl.first()
            if (url.isBlank()) "http://localhost:8000" else url.trimEnd('/')
        }
        val original: Request = chain.request()
        // Replace the host/scheme with what's saved in prefs
        val newUrl = original.url.newBuilder()
            .scheme(if (baseUrl.startsWith("https")) "https" else "http")
            .host(
                baseUrl
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .split(":")[0]
            )
            .port(
                baseUrl
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .split(":")
                    .getOrNull(1)
                    ?.split("/")
                    ?.firstOrNull()
                    ?.toIntOrNull()
                    ?: if (baseUrl.startsWith("https")) 443 else 80
            )
            .build()
        val newRequest = original.newBuilder().url(newUrl).build()
        return chain.proceed(newRequest)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AttendanceDatabase =
        Room.databaseBuilder(
            context,
            AttendanceDatabase::class.java,
            AttendanceDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideAttendanceDao(db: AttendanceDatabase): AttendanceDao = db.attendanceDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(prefs: AppPreferences): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(DynamicUrlInterceptor(prefs))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        // Base URL is a stub — DynamicUrlInterceptor rewrites it per-request
        Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): EhazirApiService =
        retrofit.create(EhazirApiService::class.java)
}
