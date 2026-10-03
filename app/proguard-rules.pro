# ProGuard rules for NewsFlow English

# Keep Compose
-keep class androidx.compose.** { *; }

# Keep Retrofit/Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.* <fields>;
}

# Keep model classes
-keep class com.newsflow.app.data.** { *; }

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
