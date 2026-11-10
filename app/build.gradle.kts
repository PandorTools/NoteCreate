plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pandortools.notecreate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pandortools.notecreate"
        minSdk = 29
        targetSdk = 35
        versionCode = 6
        versionName = "0.1.3"
    }

    signingConfigs {
        create("release") {
            val keyPath = providers.gradleProperty("NOTE_CREATE_KEYSTORE_PATH").orNull
                ?: System.getenv("NOTE_CREATE_KEYSTORE_PATH")
                ?: "${System.getProperty("user.home")}/.android/debug.keystore"
            storeFile = file(keyPath)
            storePassword = providers.gradleProperty("NOTE_CREATE_STORE_PASSWORD").orNull
                ?: System.getenv("NOTE_CREATE_STORE_PASSWORD") ?: "android"
            keyAlias = providers.gradleProperty("NOTE_CREATE_KEY_ALIAS").orNull
                ?: System.getenv("NOTE_CREATE_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = providers.gradleProperty("NOTE_CREATE_KEY_PASSWORD").orNull
                ?: System.getenv("NOTE_CREATE_KEY_PASSWORD") ?: "android"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
