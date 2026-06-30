# ─────────────────────────────────────────────────────────────────────────────
# Samra R8 keep rules. Enable minify in the release build (build.gradle.kts).
# ─────────────────────────────────────────────────────────────────────────────

# Chaquopy runtime.
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# CRITICAL: the Python engine (samra.py) calls these Kotlin callback methods BY NAME
# via Chaquopy reflection (self.kt.onLog/onProgress/onBook). R8 must never rename them.
-keep interface com.samra.downloader.SamraListener { *; }
-keepclassmembers class * implements com.samra.downloader.SamraListener {
    public <methods>;
}

# Tink / androidx.security-crypto register key managers via reflection
# (EncryptedSharedPreferences credential storage). Keep them intact.
-keep class com.google.crypto.tink.** { *; }
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.crypto.tink.**

# Media3 ships its own consumer rules; keep the session/service surface as a safety net
# so background playback + media notification/lockscreen controls keep working.
-keep class androidx.media3.session.** { *; }
-dontwarn androidx.media3.**

# org.json is used directly (no reflection) — no rules needed.
