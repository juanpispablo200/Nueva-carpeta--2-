# ══════════════════════════════════════════════════════════════════════════════
# Jagoo — ProGuard / R8 rules
# Probadas con: Supabase-kt 3.1.4, Ktor 3.1.3, Firebase BOM 33.7, Maps 4.4
# ══════════════════════════════════════════════════════════════════════════════

# ── Atributos esenciales ──────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { <fields>; }

# ── Kotlin Serialization ──────────────────────────────────────────────────────
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.jagoo.app.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.jagoo.app.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.jagoo.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Clases de serialización de Supabase / kotlinx
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Coroutines ────────────────────────────────────────────────────────────────
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Supabase-kt ───────────────────────────────────────────────────────────────
-keep class io.github.jan.supabase.** { *; }
-keepclassmembers class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# ── Ktor ──────────────────────────────────────────────────────────────────────
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**
# Ktor usa reflection para registrar plugins
-keepnames class io.ktor.client.engine.android.**
-keep class io.ktor.client.plugins.** { *; }

# ── OkHttp / Okio (usado por Ktor internamente) ───────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── Firebase / FCM ────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
# FirebaseMessagingService — no puede ser obfuscado
-keep class com.jagoo.app.notifications.JagooFirebaseMessagingService { *; }
-keep class com.jagoo.app.notifications.JagooNotificationManager { *; }

# ── Google Maps / Compose Maps ────────────────────────────────────────────────
-keep class com.google.maps.android.** { *; }
-keep class com.google.android.gms.maps.** { *; }
-dontwarn com.google.maps.android.**

# ── Coil ──────────────────────────────────────────────────────────────────────
-dontwarn coil.**
-keep class coil.** { *; }

# ── Compose ───────────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ── Data classes y DTOs de Jagoo ─────────────────────────────────────────────
# Supabase decodifica JSON a data classes por reflection → no obfuscar
-keep class com.jagoo.app.data.** { *; }
-keepclassmembers class com.jagoo.app.data.** { *; }
-keep class com.jagoo.app.viewmodel.**Dto { *; }
-keep class com.jagoo.app.viewmodel.DriverEarningsDto { *; }
-keep class com.jagoo.app.viewmodel.SettingRow { *; }
-keep class com.jagoo.app.viewmodel.BankInfo { *; }
-keep class com.jagoo.app.ui.screens.DriverLocationDto { *; }

# ── Enums ────────────────────────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── R8 — evitar warnings de dependencias internas ────────────────────────────
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
