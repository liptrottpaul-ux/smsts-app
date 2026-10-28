import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Firebase is optional: drop a google-services.json into app/ to enable Firestore sync.
// Without it the app builds and runs with on-device persistence only.
val hasGoogleServices = file("google-services.json").exists()
if (hasGoogleServices) {
    apply(plugin = "com.google.gms.google-services")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Offline neural voices (sherpa-onnx running Piper models). The ~38 MB AAR is fetched once from
// the official release and checked against a known hash rather than committed to git.
val sherpaVersion = "1.13.8"
val sherpaSha256 = "b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471"
val sherpaAar = file("libs/sherpa-onnx-$sherpaVersion.aar")
if (!sherpaAar.exists()) {
    sherpaAar.parentFile.mkdirs()
    val part = file("${sherpaAar.path}.part")
    val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/" +
        "sherpa-onnx-static-link-onnxruntime-$sherpaVersion.aar"
    uri(url).toURL().openStream().use { input -> part.outputStream().use { input.copyTo(it) } }
    val digest = MessageDigest.getInstance("SHA-256").digest(part.readBytes())
        .joinToString("") { "%02x".format(it) }
    check(digest == sherpaSha256) { "sherpa-onnx AAR checksum mismatch: $digest" }
    part.renameTo(sherpaAar)
}

fun secret(name: String): String =
    (localProps.getProperty(name) ?: System.getenv(name) ?: "").trim().replace("\"", "")

android {
    namespace = "uk.co.asmcoach"
    compileSdk = 35

    defaultConfig {
        applicationId = "uk.co.asmcoach"
        minSdk = 26
        targetSdk = 35
        // CI stamps each build with its run number so the in-app updater can compare versions.
        val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"

        buildConfigField("String", "GEMINI_API_KEY", "\"${secret("GEMINI_API_KEY")}\"")
        buildConfigField("String", "REED_API_KEY", "\"${secret("REED_API_KEY")}\"")
        buildConfigField("String", "SERPER_API_KEY", "\"${secret("SERPER_API_KEY")}\"")
        buildConfigField("String", "FIRECRAWL_API_KEY", "\"${secret("FIRECRAWL_API_KEY")}\"")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", hasGoogleServices.toString())

        // Phones only: the neural voice engine is native code, and x86 builds would double its size.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // A committed debug keystore keeps the signature identical across CI builds, so a new APK
    // installs as an update instead of failing with "package conflicts". Debug-only; not secret.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sideload-friendly: sign release with the debug key unless you add your own config.
            signingConfig = signingConfigs.getByName("debug")
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
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Compress native libraries in the APK (the voice engine is large); Android extracts them on install.
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.haze)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(files(sherpaAar))
    implementation(libs.commons.compress)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
