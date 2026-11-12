plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Nombor build datang dari GitHub Actions supaya setiap APK baru boleh "update" APK lama.
val buildNumber: Int = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "my.autotainment.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "my.autotainment.app"
        minSdk = 31
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    // Keystore tetap dalam repo: semua build ditandatangan dengan kunci yang sama,
    // jadi update APK tak perlu uninstall dulu. Ini kunci debug, bukan untuk Play Store.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Dua edisi:
    //  - lite: tiada Notification Listener -> boleh install terus dari browser
    //          (Play Protect Malaysia block app sideload yang minta permission ini)
    //  - full: ada paparan lagu (Notification Listener) -> install melalui ADB dari PC
    flavorDimensions += "edition"
    productFlavors {
        create("lite") {
            dimension = "edition"
            versionNameSuffix = "-lite"
        }
        create("full") {
            dimension = "edition"
            versionNameSuffix = "-full"
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
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
