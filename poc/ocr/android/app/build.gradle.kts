plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.calcsuits.poc.ocr"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.calcsuits.poc.ocr.arm64"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-poc-phase4.1"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    // On-device ONNX Runtime Android (MIT License, arm64-v8a MatMulNBits verified)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
