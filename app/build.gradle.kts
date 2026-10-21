plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kilat.motion"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kilat.motion"
        minSdk = 29
        targetSdk = 34
        versionCode = 18
        versionName = "1.16.0"

        // Client ID Google (publik, bukan secret). Isi di gradle.properties:
        //   googleClientIdAndroid=XXXX.apps.googleusercontent.com
        val gClientId = (project.findProperty("googleClientIdAndroid") as String?)
            ?: System.getenv("GOOGLE_CLIENT_ID_ANDROID") ?: ""
        val gScheme = if (gClientId.endsWith(".apps.googleusercontent.com"))
            "com.googleusercontent.apps." + gClientId.removeSuffix(".apps.googleusercontent.com")
        else "kilat-unconfigured"
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"$gClientId\"")
        buildConfigField("String", "GOOGLE_REDIRECT_SCHEME", "\"$gScheme\"")
        manifestPlaceholders["googleRedirectScheme"] = gScheme
    }

    buildTypes {
        release {
            // R8 aktif: Material/AndroidX ikut masuk, tanpa shrink APK release jadi ~10 MB
            isMinifyEnabled = true
            isShrinkResources = true
            // supaya APK release bisa langsung di-install (sideload) tanpa keystore sendiri
            signingConfig = signingConfigs.getByName("debug")
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
    // Komponen editor tetap custom (Canvas/View) agar tampilan flat KILAT konsisten.
    implementation("com.google.android.material:material:1.12.0")
    // test: JUnit untuk memverifikasi engine animasi di JVM
    testImplementation("junit:junit:4.13.2")
    // org.json asli untuk test JVM (stub Android hanya mengembalikan nilai kosong)
    testImplementation("org.json:json:20231013")
}
