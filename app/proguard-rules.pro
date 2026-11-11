# ProGuard / R8 rules for AMBAY Billing

# Keep data models serialized to JSON
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Room entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class * { *; }

# Keep Bouncy Castle providers
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes *Annotation*

# Keep JavaScript Interface for WebView
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep ML Kit Barcode
-keep class com.google.mlkit.vision.barcode.** { *; }

# Keep Credentials Manager and Google Identity
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
