plugins {
    // Match the toolchain used by SukiSU Ultra's current liquid implementation.
    // Keeping Compose/AGP from one stack avoids runtime linkage crashes.
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
