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
        versionCode = 14
        versionName = "1.12.0"
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
}
