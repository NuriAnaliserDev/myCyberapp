# Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View

# Keep setters in Views so that animations can still work.
-keepclassmembers public class * extends android.view.View {
   void set*(***);
   *** get*();
}

# We want to keep methods in Activity that could be used in the XML attribute onClick
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# AndroidX Security
-keep class androidx.security.crypto.** { *; }

# AndroidX Biometric
-keep class androidx.biometric.** { *; }

# Keep custom data classes used for JSON parsing if any (we use JSONObject manually so less critical, but good practice)
-keepclassmembers class com.example.cyberapp.** {
    <fields>;
}

# Keep CyberApp critical classes and their public methods
-keep class com.example.cyberapp.RootDetector {
    public <methods>;
}
-keep class com.example.cyberapp.EncryptedLogger {
    public <methods>;
}
-keep class com.example.cyberapp.LoggerService {
    public <methods>;
}
-keep class com.example.cyberapp.CyberVpnService {
    public <methods>;
}
-keep class com.example.cyberapp.BiometricAuthManager {
    public <methods>;
}
-keep class com.example.cyberapp.PinManager {
    public <methods>;
}
-keep class com.example.cyberapp.EncryptedPrefsManager {
    public <methods>;
}
-keep class com.example.cyberapp.NetworkStatsHelper {
    public <methods>;
}

# Keep Activities
-keep class com.example.cyberapp.MainActivity { *; }
-keep class com.example.cyberapp.PinActivity { *; }
-keep class com.example.cyberapp.SettingsActivity { *; }
-keep class com.example.cyberapp.AppAnalysisActivity { *; }

# Keep Application class
-keep class com.example.cyberapp.CyberApp { *; }

# Optimization settings
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose

# Fix for missing class javax.annotation.Nullable (referenced by Tink/Guava)
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**

# ==================== CRITICAL: Data Classes ====================
# Keep all data classes - they are used with JSON parsing and reflection
-keep class com.example.cyberapp.Anomaly { *; }
-keep class com.example.cyberapp.AppInfo { *; }
-keep class com.example.cyberapp.SecurityCheckResult { *; }
-keep class com.example.cyberapp.PhishingDetector$AnalysisResult { *; }
-keep class com.example.cyberapp.NetworkStatsHelper$NetworkUsage { *; }

# Network API data classes (used with Retrofit/Gson)
-keep class com.example.cyberapp.network.** { *; }

# Module data classes
-keep class com.example.cyberapp.modules.apk_scanner.AppInfo { *; }

# ==================== CRITICAL: Adapters & ViewHolders ====================
# RecyclerView Adapters and ViewHolders must be kept
-keep class com.example.cyberapp.AnomalyAdapter { *; }
-keep class com.example.cyberapp.AnomalyAdapter$* { *; }
-keep class com.example.cyberapp.AppAdapter { *; }
-keep class com.example.cyberapp.AppAdapter$* { *; }
-keep class com.example.cyberapp.modules.apk_scanner.AppAdapter { *; }
-keep class com.example.cyberapp.modules.apk_scanner.AppAdapter$* { *; }

# ==================== CRITICAL: Interfaces ====================
# Keep all interfaces (especially listeners)
-keep interface com.example.cyberapp.** { *; }

# ==================== Retrofit & OkHttp ====================
# Retrofit does reflection on generic parameters
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn org.codehaus.mojo.animal_sniffer.*

# OkHttp platform used only on JVM and when Conscrypt and other security providers are available.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ==================== Room Database ====================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ==================== Gson ====================
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ==================== Kotlin Coroutines ====================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ==================== CRITICAL: Keep all constructors ====================
# This prevents crashes when creating instances via reflection
-keepclassmembers class com.example.cyberapp.** {
    public <init>(...);
}