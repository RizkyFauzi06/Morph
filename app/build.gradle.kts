import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.morph.app"
    compileSdk = 37
    // Offline toolchain in C:\Android\Sdk (ndk;27.2.12479018, cmake;3.22.1)
    ndkVersion = "27.2.12479018"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        applicationId = "com.morph.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "1.1.1"

        externalNativeBuild {
            cmake {
                // FFmpegKit ships only arm64-v8a and x86_64; x86_64 (emulators) falls back to libopenh264
                abiFilters += "arm64-v8a"
            }
        }

        // Package only what FFmpegKit supports. Compose's libandroidx.graphics.path.so also comes for
        // armeabi-v7a/x86, which made the APK installable on 32-bit phones where FFmpegKit can't load
        // and the first video or audio conversion would crash. 32-bit phones now refuse the install.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            storeFile = file(localProps.getProperty("RELEASE_STORE_FILE") ?: "")
            storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD") ?: ""
            keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS") ?: ""
            keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            // R8 shrinks and optimizes the code (Compose runs much faster than unoptimized) and drops
            // unused resources. JNI and FFmpegKit entry points are kept in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // fork komunitas yang aktif di-maintain tahun ini (full variant: includes libvpx, libvorbis, opus, lame)
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7")
    // Tambahkan baris di bawah ini untuk menambal crash "NoClassDefFoundError"
    implementation("com.arthenica:smart-exception-java:0.2.1")
    implementation("androidx.compose.material:material:1.5.4") // Dibutuhkan untuk pullrefresh
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    // SVG Renderer (PBI Sprint 3 - Apache 2.0)
    implementation("com.caverock:androidsvg-aar:1.4")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
}