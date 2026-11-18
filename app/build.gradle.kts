plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.test.mtclone"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.test.mtclone"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Ditandatangani dengan testkey AOSP (publik) -- cocok untuk build uji/mod pribadi,
    // bukan untuk rilis Play Store. Lihat keystore/README.txt.
    signingConfigs {
        create("aospTestkey") {
            storeFile = file("keystore/testkey.p12")
            storeType = "pkcs12"
            storePassword = "android"
            keyAlias = "testkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("aospTestkey")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("aospTestkey")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes.add("META-INF/*")
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Baca & tulis smali dari/ke classes.dex. Koordinat dicek lewat pom di Maven Central.
    implementation("org.smali:dexlib2:2.5.2")
    implementation("org.smali:baksmali:2.5.2")
    implementation("org.smali:util:2.5.2")

    // Tanda tangan APK v1/v2/v3 di dalam aplikasi (fitur "Tanda tangani file"), pakai testkey AOSP.
    implementation("com.android.tools.build:apksig:8.5.2")
}
