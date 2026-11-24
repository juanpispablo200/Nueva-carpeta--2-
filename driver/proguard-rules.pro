# ══════════════════════════════════════════════════════════════════════════════
# Jagoo Driver — ProGuard / R8 rules
# ══════════════════════════════════════════════════════════════════════════════

-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepattributes Signature, Exceptions
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ── Kotlin Serialization ──────────────────────────────────────────────────────
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.jagoo.driver.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.jagoo.driver.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }

# ── Coroutines ────────────────────────────────────────────────────────────────
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── Supabase-kt ───────────────────────────────────────────────────────────────
-keep class io.github.jan.supabase.** { *; }
-keepclassmembers class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# ── Ktor ──────────────────────────────────────────────────────────────────────
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**
-keepnames class io.ktor.client.engine.android.**

# ── OkHttp / Okio ─────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Firebase / FCM ────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-keep class com.jagoo.driver.notifications.DriverFirebaseMessagingService { *; }
-keep class com.jagoo.driver.notifications.DriverNotificationManager { *; }

# ── Location Service ─────────────────────────────────────────────────────────
-keep class com.jagoo.driver.location.DriverLocationService { *; }

# ── Google Maps ───────────────────────────────────────────────────────────────
-keep class com.google.maps.android.** { *; }
-keep class com.google.android.gms.maps.** { *; }
-dontwarn com.google.maps.android.**

# ── Compose ───────────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Data classes del Driver ───────────────────────────────────────────────────
-keep class com.jagoo.driver.data.** { *; }
-keepclassmembers class com.jagoo.driver.data.** { *; }
-keep class com.jagoo.driver.viewmodel.DriverEarningsDto { *; }
-keep class com.jagoo.driver.viewmodel.DriverOrderDto { *; }

# ── Enums ────────────────────────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# -- Crashlytics: conservar excepciones propias para reportes legibles
-keep public class * extends java.lang.Exception
