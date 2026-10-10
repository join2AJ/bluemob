plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
    alias(libs.plugins.ksp)
}

android {
    // Native libraries (the offline-AI engine is the big one) are compressed in the APK, so downloads stay small.
    packaging { jniLibs { useLegacyPackaging = true } }
    namespace = "com.bluemob.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bluemob.app"
        minSdk = 26
        // The BlueMob relay every phone uses for the internet bridge, built in so nobody has to type it.
        // Set `relayUrl=https://…` in gradle.properties once the relay is deployed (see server/README.md).
        buildConfigField("String", "DEFAULT_RELAY_URL", "\"" + ((project.findProperty("relayUrl") as String?) ?: "") + "\"")
        // Firebase (optional): wakes the phone for calls and messages when BlueMob is closed, and sends real SMS codes
        // at sign-up. Fill these in gradle.properties from the Firebase console (see docs/firebase.md); left empty,
        // BlueMob works as before with the test code 123456.
        listOf("firebaseAppId", "firebaseApiKey", "firebaseProjectId", "firebaseSenderId").forEach { key ->
            buildConfigField("String", key.replaceFirstChar { it.uppercase() }.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase(),
                "\"" + ((project.findProperty(key) as String?) ?: "") + "\"")
        }
        // Optional: `-PonlyAbi=arm64-v8a` builds for one phone type only (a smaller APK to share for testing).
        (project.findProperty("onlyAbi") as String?)?.let { ndk { abiFilters += it } }
        targetSdk = 35
        versionCode = 31
        versionName = "0.21.0"
    }

    signingConfigs {
        // Test builds are signed with this debug key, kept in the repo so every build (from any machine) installs over the
        // last one and keeps the same fingerprint for Firebase. Debug/test only: release builds need their own key.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // Shrinks and obfuscates the code, so the app is much harder to reverse-engineer.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // Play Services pulls in an old Fragment that breaks the permission-request API.
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.play.services.nearby)
    implementation(libs.kotlinx.coroutines.play.services)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)
    // Video calls: camera frames for the low-bandwidth video sent over the mesh.
    implementation(libs.camerax.camera2)
    // Fingerprint / face unlock for the BlueMob login.
    implementation(libs.androidx.biometric)
    // Internet calls: a WebSocket to the BlueMob relay.
    implementation(libs.okhttp)
    // Sky's optional offline AI: runs a downloaded language model on the phone (the model itself is not in the APK).
    implementation(libs.mediapipe.genai)
    // Scheduled encrypted backups.
    implementation(libs.work.runtime)
    // Optional wake-ups for calls and messages when closed, and real SMS codes (only used when configured).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.auth)
    implementation(libs.camerax.lifecycle)
    testImplementation("org.json:json:20240303")
    testImplementation(libs.robolectric)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
