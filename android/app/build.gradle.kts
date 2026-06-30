import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.chaquopy)
}

// Release signing — loaded from the git-ignored keystore.properties (passwords never in VCS).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

// Machine-specific Chaquopy buildPython path — kept OUT of VCS (local.properties / env),
// with a generic fallback so other machines / CI can supply their own Python 3.12.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
val samraBuildPython: String = localProps.getProperty("samra.buildPython")
    ?: System.getenv("SAMRA_BUILD_PYTHON")
    ?: "python3"

android {
    namespace = "com.samra.downloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.samra.downloader"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"

        ndk {
            // This device is arm64; keep the APK lean by shipping one ABI.
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Sign release builds with the real release key (not the debug key).
            if (keystorePropsFile.exists()) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // Extract native libs to nativeLibraryDir at install so the bundled
            // ffmpeg (libffmpeg.so) is a real on-disk file we can exec.
            useLegacyPackaging = true
            // Don't let AGP run strip on the ffmpeg executable.
            keepDebugSymbols += "**/libffmpeg.so"
        }
    }
}

chaquopy {
    defaultConfig {
        // Match the build machine's Python (PC: winget Python 3.12).
        version = "3.12"
        buildPython(samraBuildPython)

        pip {
            // audiobook-dl runtime dependencies — PINNED to exact, current/patched versions
            // for reproducible builds and supply-chain safety (security review H5).
            install("appdirs==1.4.4")
            install("attrs==26.1.0")
            install("cssselect==1.4.0")
            install("importlib-resources==7.1.0")
            install("lxml==5.3.0")
            install("m3u8==6.0.0")
            install("mutagen==1.48.1")
            install("pycountry==26.2.16")
            install("pycryptodome==3.21.0")
            install("python-dateutil==2.9.0.post0")
            install("requests==2.34.2")
            install("rich==15.0.0")
            install("tomli==2.4.1")
            install("urllib3==2.7.0")
            install("Pillow==11.0.0")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.security.crypto)
    debugImplementation(libs.androidx.ui.tooling)
}
