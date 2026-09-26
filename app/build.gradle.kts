plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "nz.afhome.ledger"
    compileSdk = 36

    defaultConfig {
        applicationId = "nz.afhome.ledger"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.1"
        // The realme GT Master is arm64.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        debug {
            // Build-type ABI filters are merged with defaultConfig: debug adds x86_64 for the PC emulator.
            ndk { abiFilters += "x86_64" }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // `./gradlew assembleRelease -Pemu` also packs x86_64 so the shrunk build can be tested on the emulator.
            if (project.hasProperty("emu")) ndk { abiFilters += "x86_64" }
            // Signed with the debug key so it can be side-loaded straight onto the phone.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.02.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("androidx.work:work-runtime-ktx:2.11.0")

    val room = "2.8.4"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // On-device OCR — the "bundled" artifact ships the model inside the APK (no download, no network).
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // On-device LLM runtime for Gemma models (.litertlm files).
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")

    testImplementation("junit:junit:4.13.2")
}
