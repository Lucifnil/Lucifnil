plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kikyo.cloudlauncher"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    defaultConfig {
        applicationId = "com.kikyo.cloudlauncher"
        minSdk = 26
        targetSdk = 36
        versionCode = 23
        versionName = "1.3.1"

        buildConfigField(
            "String",
            "LAUNCHER_SCRIPT_PATH",
            "\"/data/adb/嗯嗯启动器.sh\""
        )
        buildConfigField(
            "String",
            "LAUNCHER_DIRECTORY",
            "\"/data/adb\""
        )
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Exact API-36-era SukiSU Ultra Compose line.  Backdrop 1.0.6 was built
    // and tested with this line; mixing it with the old 2024 BOM caused the
    // prior runtime linkage crash.
    val composeBom = platform("androidx.compose:compose-bom:2026.03.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha15")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // SukiSU Ultra's original live-liquid implementation: sampled backdrop,
    // blur/vibrancy/refraction and the draggable moving lens.
    implementation("io.github.kyant0:backdrop:1.0.6")
    implementation("io.github.kyant0:capsule:2.1.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
