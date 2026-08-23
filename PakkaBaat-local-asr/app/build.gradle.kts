import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Load keys from gradle.properties (or a local.properties override if present)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(FileInputStream(f))
}
fun cfg(key: String): String =
    (localProps.getProperty(key) ?: project.findProperty(key) as String? ?: "")

android {
    namespace = "com.pakkabaat.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pakkabaat.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-local-asr"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Bhashini is gone on this branch — ASR is fully local via whisper.cpp now.
        // Gemini is kept for structuring only. GEMINI_API_KEY here is an optional
        // fallback; the Settings screen lets each tester enter their own free key
        // instead (BYOK), which is what actually gets used if present.
        buildConfigField("String", "GEMINI_API_KEY", "\"${cfg("PAKKABAAT_GEMINI_API_KEY")}\"")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a") // covers virtually all real Android phones
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // The bundled Whisper model (~57MB) is a real audio model file, not text — don't
    // let the build tools try to compress it further or split it oddly across ABIs.
    androidResources {
        noCompress += "bin"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Room (local-first datastore, per spec section 9)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Networking (Gemini structuring LLM — free tier; ASR is now on-device, see recording/)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Nearby Connections (in-person device-to-device pairing, no server / no internet needed)
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    // QR code generation + scanning (local proximity pairing, spec section 8.2)
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // WorkManager (queued cloud processing when connectivity returns, spec section 6.4 / 8.7)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Security (encryption at rest, spec section 13)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
