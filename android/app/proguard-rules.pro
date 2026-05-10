# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Retrofit & Gson models
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.ehazir.companion.data.remote.** { *; }
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep interface retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep Room entities
-keep class com.ehazir.companion.data.local.** { *; }

# Keep Hilt-generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# WorkManager
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

-dontwarn java.lang.reflect.AnnotatedType
