plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.morph.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.morph.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.8"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // fork komunitas yang aktif di-maintain tahun ini
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-min:8.1.7")
    // Tambahkan baris di bawah ini untuk menambal crash "NoClassDefFoundError"
    implementation("com.arthenica:smart-exception-java:0.2.1")
    implementation("androidx.compose.material:material:1.5.4") // Dibutuhkan untuk pullrefresh
}