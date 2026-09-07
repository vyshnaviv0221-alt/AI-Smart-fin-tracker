import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// server.baseUrl comes from local.properties, which is git-ignored, so a
// developer's own machine setup is never committed. See
// client/local.properties.example.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun localProperty(key: String): String = localProperties.getProperty(key).orEmpty()

android {
    namespace = "com.example.aismartexpensetracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.aismartexpensetracker"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Base URL of the local ML server.
        // Port 8081 on BOTH sides of the USB bridge, so there is one number
        // to remember. Not 8000: it is heavily used on Android, and a busy
        // device port makes `adb reverse` fail.
        //   real phone  -> http://127.0.0.1:8081/  (start-server.bat does the reverse)
        //   emulator    -> http://10.0.2.2:8081/
        //   same Wi-Fi  -> http://<laptop-LAN-IP>:8081/
        buildConfigField(
            "String",
            "ML_SERVER_URL",
            "\"${localProperty("server.baseUrl").ifBlank { "http://127.0.0.1:8081/" }}\""
        )
    }

    signingConfigs {
        // The release build is signed with the debug key so it can be
        // installed and profiled locally. A debug build is not representative
        // of runtime performance: it is debuggable, skips R8, and runs Compose
        // with extra instrumentation. Replace this before any real
        // distribution.
        create("localRelease") {
            val debugStore = File(System.getProperty("user.home"), ".android/debug.keystore")
            if (debugStore.exists()) {
                storeFile = debugStore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            // Minification stays off: Retrofit and Gson resolve the network
            // models reflectively, so enabling R8 needs keep rules that have
            // not been written or tested yet. The measurable win here comes
            // from the build no longer being debuggable.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("localRelease")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // ExpenseRepository calls android.util.Log, which is a stub on the
            // JVM and throws by default. Returning defaults lets the repository
            // logic be tested without an emulator.
            isReturnDefaultValues = true
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Networking -- the ML server's REST API.
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ViewModel + Compose integration
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Navigation -- required by ui/MenuScreen.kt and MainActivity's NavHost
    implementation("androidx.navigation:navigation-compose:2.10.0")
}
