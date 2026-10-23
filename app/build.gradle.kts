import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fluxa.motion"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fluxa.motion"
        minSdk = 29
        targetSdk = 34
        versionCode = 25
        versionName = "1.21.0"

        // Web client ID Google (publik, bukan secret) untuk Credential Manager.
        // Isi di gradle.properties: googleWebClientId=XXXX.apps.googleusercontent.com
        val gWebClientId = (project.findProperty("googleWebClientId") as String?)
            ?: System.getenv("GOOGLE_WEB_CLIENT_ID") ?: ""
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$gWebClientId\"")

        // Buang resource bahasa dari library (Material/AndroidX) yang tidak dipakai app.
        // Tanpa ini resources.arsc membengkak. Default (Indonesia) tetap ikut.
        resourceConfigurations += listOf("en", "in", "id")
    }

    // Satu APK per arsitektur (arm64-v8a untuk HP modern, armeabi-v7a untuk HP lama, x86_64 untuk emulator).
    // Tidak ada APK universal agar file yang diunduh lebih kecil. Pilih yang sesuai HP.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    // Native kecil untuk timestamp export (libfluxa_timing.so). Tanpa .so, export pakai jalur real-time.
    ndkVersion = "26.1.10909125"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Keystore rilis tetap (dari GitHub Secrets di CI, atau env lokal). Tanpa itu, pakai debug.
    val kilatKeystore: String? = System.getenv("KILAT_KEYSTORE_PATH")?.takeIf { File(it).exists() }

    // Di CI, keystore rilis wajib ada. Tanpa ini release diam-diam ditandatangani kunci debug.
    if (System.getenv("CI") == "true" && kilatKeystore == null) {
        throw GradleException("CI: keystore rilis tidak ada (KILAT_KEYSTORE_PATH). Secret KILAT_KEYSTORE_B64 kosong atau gagal didekode.")
    }

    signingConfigs {
        create("fluxa") {
            if (kilatKeystore != null) {
                storeFile = File(kilatKeystore)
                storePassword = System.getenv("KILAT_STORE_PASSWORD")
                // alias ini nama entri di dalam keystore rilis yang sudah ada: JANGAN diganti
                keyAlias = System.getenv("KILAT_KEY_ALIAS") ?: "kilat"
                keyPassword = System.getenv("KILAT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // debug juga ditandatangani kunci tetap agar SHA-1-nya sama dengan release
            if (kilatKeystore != null) signingConfig = signingConfigs.getByName("fluxa")
        }
        release {
            // R8 aktif: Material/AndroidX ikut masuk, tanpa shrink APK release jadi ~10 MB
            isMinifyEnabled = true
            isShrinkResources = true
            // supaya APK release bisa langsung di-install (sideload) tanpa keystore sendiri
            signingConfig = signingConfigs.getByName(if (kilatKeystore != null) "fluxa" else "debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module")
    }
}

dependencies {
    // UI: Material Components (Material3) untuk tema dasar, widget default, dan kontrol sistem.
    // Komponen editor tetap custom (Canvas/View) agar tampilan flat FLUXA konsisten.
    implementation("com.google.android.material:material:1.12.0")
    // login Google native lewat Credential Manager (tanpa browser)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // test: JUnit untuk memverifikasi engine animasi di JVM
    testImplementation("junit:junit:4.13.2")
    // org.json asli untuk test JVM (stub Android hanya mengembalikan nilai kosong)
    testImplementation("org.json:json:20231013")
}
