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