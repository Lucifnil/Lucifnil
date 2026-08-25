plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kikyo.cloudlauncher"
    compileSdk {
        version = release(36)
    }
    buildToolsVersion = "36.0.0"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    defaultConfig {
        applicationId = "com.kikyo.cloudlauncher"
        minSdk = 26
        targetSdk = 36
        versionCode = 22
        versionName = "1.3.0"

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
    // This is the same Compose line used by SukiSU Ultra with miuix-blur.
    // Do not mix it with the old 2024 BOM: that was the previous crash path.
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha24")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // SukiSU Ultra's actual liquid stack (not a reimplementation).
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
