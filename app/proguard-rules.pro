# AMBAY Billing v2 R8 / ProGuard keep rules (used when -PAMBAY_MINIFY=true)

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.ambay.billing.**$$serializer { *; }
-keepclassmembers class com.ambay.billing.** {
    *** Companion;
}
-keepclasseswithmembers class com.ambay.billing.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Retrofit & OkHttp
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn okhttp3.**

# ML Kit Barcode Scanning
-keep class com.google.mlkit.vision.barcode.** { *; }
-dontwarn com.google.mlkit.vision.common.**

# BouncyCastle (Ed25519 / X.509)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
