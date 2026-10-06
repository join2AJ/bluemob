plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.bluemob.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bluemob.app"
        minSdk = 26
        // The BlueMob relay every phone uses for the internet bridge, built in so nobody has to type it.
        // Set `relayUrl=https://…` in gradle.properties once the relay is deployed (see server/README.md).
        buildConfigField("String", "DEFAULT_RELAY_URL", "\"" + ((project.findProperty("relayUrl") as String?) ?: "") + "\"")
        // Optional: `-PonlyAbi=arm64-v8a` builds for one phone type only (a smaller APK to share for testing).
        (project.findProperty("onlyAbi") as String?)?.let { ndk { abiFilters += it } }
        targetSdk = 35
        versionCode = 16
        versionName = "0.11.2"
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
    // Scheduled encrypted backups.
    implementation(libs.work.runtime)
    implementation(libs.camerax.lifecycle)
    testImplementation("org.json:json:20240303")
    testImplementation(libs.robolectric)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
