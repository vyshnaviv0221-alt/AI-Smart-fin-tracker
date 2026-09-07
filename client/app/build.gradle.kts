import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Supabase URL and anon key come from local.properties, which is git-ignored,
// so credentials are never committed. See client/local.properties.example.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun localProperty(key: String): String = localProperties.getProperty(key).orEmpty()

/**
 * Refuses to build if a privileged Supabase key has been put in the client.
 *
 * Supabase issues two keys and they look similar enough to paste the wrong
 * one. The publishable/anon key is safe to ship: it identifies the project,
 * and row-level security is what actually protects the data. The secret /
 * service_role key BYPASSES row-level security, so shipping it in an APK
 * hands every user's rows to anyone who unzips the file -- and an APK is
 * trivially unzipped.
 *
 * Failing the build is the right response: a warning would be missed.
 */
fun requirePublishableKey(key: String) {
    if (key.isBlank()) return

    // Prefix matching only. Decoding the JWT payload to look for a
    // "service_role" claim was tried and dropped: it added a decode that can
    // itself fail inside the build script, to catch a legacy key format that
    // Supabase is retiring. The prefixes below cover both the current keys
    // (sb_secret_) and the older personal access tokens (sbp_).
    val privileged = listOf("sb_secret_", "sbp_", "service_role")
    if (privileged.any { key.startsWith(it) }) {
        throw GradleException(
            "supabase.anonKey in local.properties is a SECRET / service_role key. " +
                "That key bypasses row-level security, so shipping it in an APK exposes " +
                "every user's data to anyone who unzips the file. " +
                "Use the publishable (anon) key from Project Settings > API instead, " +
                "and rotate the secret key."
        )
    }
}

requirePublishableKey(localProperty("supabase.anonKey"))

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
        //   real phone  -> http://127.0.0.1:8000/  with `adb reverse tcp:8000 tcp:8000`
        //   emulator    -> http://10.0.2.2:8000/
        //   same Wi-Fi  -> http://<laptop-LAN-IP>:8000/  (uvicorn --host 0.0.0.0)
        buildConfigField(
            "String",
            "ML_SERVER_URL",
            "\"${localProperty("server.baseUrl").ifBlank { "http://127.0.0.1:8000/" }}\""
        )
        buildConfigField("String", "SUPABASE_URL", "\"${localProperty("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProperty("supabase.anonKey")}\"")
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

    // Networking -- used for BOTH the local ML server and Supabase's REST API.
    // Supabase is reached over plain REST (PostgREST + GoTrue) rather than the
    // Supabase Kotlin SDK, so there is no extra dependency and no Ktor stack.
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Encrypted storage for the Supabase session token.
    implementation("androidx.security:security-crypto:1.1.0")

    // ViewModel + Compose integration
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Navigation -- required by ui/MenuScreen.kt and MainActivity's NavHost
    implementation("androidx.navigation:navigation-compose:2.10.0")
}
